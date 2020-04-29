-module(debug_eval).


%% API
-export([eval_with_bindings/2, parse_expression/1, check_bindings/3]).

parse_expression(Expression) ->
  {ok,Scanned,_} = erl_scan:string(Expression),
  {ok,_Parsed} = erl_parse:parse_exprs(Scanned).


check_bindings(Parsed, Bindings, CBindings) ->
  case erl_eval:check_command(Parsed, CBindings) of
    {error,{1,erl_lint,{unbound_var,UnboundVar}}} ->
      case int:get_binding(UnboundVar, Bindings) of
        {value, V} ->
          check_bindings(Parsed, Bindings, [{UnboundVar, V}|CBindings]);
        _ ->
          {unbound_var, UnboundVar}
      end;
    ok ->
      {ok, CBindings};
    Err ->
      {bad_cmd, Err}
  end.


eval_with_bindings(Expression, Bindings) ->
  case catch parse_expression(Expression) of
    {ok, Parsed} ->
      try
        %% erl_eval only allow used Bind in Bindings
        FixResult = check_bindings(Parsed, Bindings, []),
        case FixResult of
          {ok, NewBindings} ->
            {value, V, _} = erl_eval:exprs(Parsed, NewBindings),
            V;
          _ ->
            false
        end
      catch
        _ ->
          false
      end;
    _ ->
      false
  end.