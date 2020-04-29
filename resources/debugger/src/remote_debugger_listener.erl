-module(remote_debugger_listener).

% receives commands from remote debugger

-export([run/1, set_breakpoint/3]).

-include("process_names.hrl").
-include("remote_debugger_messages.hrl").
-include("trace_utils.hrl").

-record(state, {interpreted_modules = [] :: [module()], remote_node :: node()}).

run(Debugger) ->
  register(?RDEBUG_LISTENER, self()),
  Debugger ! #register_listener{pid = self()},
  loop(#state{}).

loop(State) ->
  NewState = receive
               Message ->
                 ?trace_message(Message),
                 handle_message(Message, State)
             end,
  loop(NewState).

handle_message(Message, State) ->
  UsesState = uses_state(Message),
  if
    UsesState -> process_message(Message, State);
    true -> process_message(Message), State
  end.

uses_state(#interpret_modules{}) -> true;
uses_state(#debug_remote_node{}) -> true;
uses_state(#evaluate{}) -> true;
uses_state(_Message)             -> false.

process_message({interpret_modules, NewModules},
                #state{interpreted_modules = Modules} = State) when is_list(NewModules) ->
  interpret_modules(NewModules, State#state.remote_node),
%%  interpret_modules(NewModules),
  State#state{interpreted_modules = Modules ++ NewModules};
process_message({evaluate, Pid, Expression, MaybeStackPointer}, #state{remote_node = Node}=State) when is_pid(Pid), is_list(Expression) ->
  case is_top_stack(Pid, MaybeStackPointer) of
    true ->
      evaluate(Pid, Expression, MaybeStackPointer);
    _ ->
      evaluate2(Pid, Expression, MaybeStackPointer, Node)
  end,
  State;
process_message({debug_remote_node, Node, Cookie}, #state{interpreted_modules = Modules} = State) ->
  debug_remote_node(Node, Cookie, Modules), State#state{remote_node = Node}.

% commands from remote debugger
process_message({set_breakpoint, Module, Line, Condition}) when is_atom(Module),
                                                     is_integer(Line), is_list(Condition) ->
  set_breakpoint(Module, Line),
  if
    length(Condition) > 0 ->
      ensure_condition(Condition),
      int:test_at_break(Module, Line, {debug_condition, list_to_atom(Condition)});
    true ->
      pass
  end;
process_message({remove_breakpoint, Module, Line}) when is_atom(Module),
                                                        is_integer(Line) ->
  remove_breakpoint(Module, Line);
process_message({run_debugger, Module, Function, Args}) when is_atom(Module),
                                                             is_atom(Function),
                                                             is_list(Args) ->
  run_debugger(Module, Function, Args);
process_message({step_into, Pid}) when is_pid(Pid) ->
  step_into(Pid);
process_message({step_over, Pid}) when is_pid(Pid) ->
  step_over(Pid);
process_message({step_out, Pid}) when is_pid(Pid) ->
  step_out(Pid);
process_message({continue, Pid}) when is_pid(Pid) ->
  continue(Pid);
% responses from interpreter
process_message({_Meta, {eval_rsp, EvalResponse}}) ->
  evaluate_response(EvalResponse);
% other
process_message({'DOWN', _, _, _, _}) ->
  exit(normal); % this means the process being debugged has quit
process_message(UnknownMessage) ->
  io:format("unknown message: ~p", [UnknownMessage]).

set_breakpoint(Module, Line) ->
  Response = #set_breakpoint_response{
    module = Module,
    line = Line,
    status = int:break(Module, Line)
  },
  ?RDEBUG_NOTIFIER ! Response.

set_breakpoint(Module, Line, Expression) ->
  Response = #set_breakpoint_response{
    module = Module,
    line = Line,
    status = int:test_at_break(Module, Line, {?MODULE, check_val})
  },
  put({break, Module, Line}, Expression),
  ?RDEBUG_NOTIFIER ! Response.

remove_breakpoint(Module, Line) ->
  int:delete_break(Module, Line).


%%TODO handle all processes which are being debugged, not only the spawned one.
run_debugger(Module, Function, ArgsString) ->
  case parse_args(ArgsString) of
    error ->
      %%TODO report error
      exit(normal);
    ArgsList ->
      spawn_opt(Module, Function, ArgsList, [monitor])
  end.

step_into(Pid) ->
  update_break_state(Pid),
  int:step(Pid).

step_over(Pid) ->
  update_break_state(Pid),
  int:next(Pid).

step_out(Pid) ->
  update_break_state(Pid),
  int:finish(Pid).


continue(Pid) ->
  update_break_state(Pid),
  int:continue(Pid).


%% check if need send breakpoint_reached again, because continue only deal one pid
%% but there may have more than one pid break
update_break_state(ExceptPid) ->
  Snapshots = remote_debugger_notifier:snapshot_with_stacks(),
  Snapshots2 = [E||{Pid, _, _, _, _}=E<-Snapshots, Pid =/= ExceptPid],
  case Snapshots2 of
    [{_NewPid, _, _, _, _}|_] = Snapshot->
      %% set pid undefined to mark this is sync message not new
      ?RDEBUG_NOTIFIER ! #breakpoint_reached{pid = undefined, snapshot = Snapshot};
    _ ->
      pass
  end.

is_top_stack(_Pid, 0) ->
  true;
is_top_stack(Pid, StackPointer) ->
  case get_orignal_stack(Pid) of
    [{TopSP, _}|_] ->
      StackPointer == TopSP;
    _ ->
      false
  end.


evaluate2(Pid, Expression, StackPointer, Node) ->
  {ok, Meta} = get_meta(Pid),
  R = case catch debug_eval:parse_expression(Expression) of
        {ok, Parsed} ->
          try
            Bindings = get_bindings(Meta, StackPointer),
            %% erl_eval only allow used Bind in Bindings
            FixResult = debug_eval:check_bindings(Parsed, Bindings, []),
            case FixResult of
              {ok, NewBindings} ->
                {value, V, _} = rpc:call(Node, erl_eval, exprs, [Parsed, NewBindings]),
                {local_eval_mode, V};
              _ ->
                FixResult
            end
          catch
            ErrType:ErrReason ->
              {local_eval_error, Parsed, ErrType, ErrReason}
          end;
        _ ->
          'local eval parse failed!'
      end,
  evaluate_response(R).

evaluate(Pid, Expression, MaybeStackPointer) ->
  {ok, Meta} = get_meta(Pid),
  MetaArgsListWithSP = case MaybeStackPointer =:= 0 of
                         true -> {?MODULE, Expression};
                         false -> {?MODULE, Expression, MaybeStackPointer}
                       end,
  dbg_icmd:eval(Meta,MetaArgsListWithSP).


get_bindings(Meta, SP) ->
  int:meta(Meta, bindings, SP).

get_meta(Pid) ->
  dbg_iserver:call({get_meta, Pid}).

evaluate_response(EvalResponse) ->
  ?RDEBUG_NOTIFIER ! #evaluate_response{result = EvalResponse}.

parse_args(ArgsString) ->
  case erl_scan:string(ArgsString ++ ".") of
    {ok, Tokens, _} ->
      case erl_parse:parse_exprs(Tokens) of
        {ok, ExprList} ->
          eval_argslist(ExprList);
        _ ->
          error
      end;
    _ ->
      error
  end.

eval_argslist(ExprList) ->
  case erl_eval:expr_list(ExprList, erl_eval:new_bindings()) of
    {[ArgsList | _], _} ->
      ArgsList;
    _ ->
      error
  end.

debug_remote_node(Node, Cookie, _Modules) ->
  NodeConnected = connect_to_remote_node(Node, Cookie),
  Status = if
    NodeConnected -> ok;
    true -> {failed_to_connect, Node, Cookie}
  end,
  send_debug_remote_node_response(Node, Status).

send_debug_remote_node_response(Node, ok) ->
  ?RDEBUG_NOTIFIER ! #debug_remote_node_response{node = Node, status = ok};
send_debug_remote_node_response(Node, Error) ->
  ?RDEBUG_NOTIFIER ! #debug_remote_node_response{node = Node, status = {error, Error}}.

connect_to_remote_node(Node, nocookie) ->
  connect_node(Node);
connect_to_remote_node(Node, Cookie) ->
  erlang:set_cookie(Node, Cookie),
  connect_node(Node).

connect_node(Node) ->
  case erlang:function_exported(net_kernel, connect, 1) of
    true ->
      net_kernel:connect(Node);
    _ ->
      net_kernel:connect_node(Node)
  end.

interpret_modules(_Modules, undefined) ->
  ignore;
interpret_modules(Modules, Node) ->
  IntNiResults = [{Module, int:ni(Module)} || Module <- Modules],
  send_interpret_modules_response(Node, IntNiResults),
  ok.

send_interpret_modules_response(Node, IntResults) ->
  Statuses = lists:map(fun
              ({Module, {module, _}}) -> {Module, ok};
              ({Module, error}) -> {Module, int:interpretable(Module)}
            end, IntResults),
  ?RDEBUG_NOTIFIER ! #interpret_modules_response{node = Node, statuses = Statuses}.


get_orignal_stack(Pid) ->
  case get_meta(Pid) of
    {ok, MetaPid} ->
      int:meta(MetaPid, backtrace, all);
    Error ->
      io:format("Failed to obtain meta pid for ~p: ~p~n", [Pid, Error]),
      []
  end.


ensure_condition(Condition) ->
  case erlang:function_exported(debug_condition, list_to_atom(Condition), 1) of
    true ->
      pass;
    _ ->
      add_condition(Condition)
  end.

add_condition(Condition) ->
  FunString =  io_lib:format("'~s'(B) -> debug_eval:eval_with_bindings(\"~s\", B).\n",[Condition,Condition]),
  file:write_file("debug_condition.erl", FunString, [append]),
  io:format("add condition ~s~n",[Condition]),
  c:nc("debug_eval.erl"),
  c:nc("debug_condition.erl", [nowarn_export_all]).