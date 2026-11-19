create or replace function public.consultar_pontos(p_cpf text, p_final_celular text)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    v_cli   clientes%rowtype;
    v_hoje  date := (now() at time zone 'America/Sao_Paulo')::date;
    v_cpf   text := regexp_replace(coalesce(p_cpf, ''), '\D', '', 'g');
    v_final text := right(regexp_replace(coalesce(p_final_celular, ''), '\D', '', 'g'), 4);
begin
    if length(v_cpf) <> 11 or length(v_final) <> 4 then
        return json_build_object('ok', false);
    end if;

    select * into v_cli from clientes where cpf = v_cpf and ativo limit 1;
    if not found or right(regexp_replace(v_cli.telefone, '\D', '', 'g'), 4) <> v_final then
        perform pg_sleep(0.6);  -- dificulta tentativas em massa
        return json_build_object('ok', false);
    end if;

    return json_build_object(
        'ok', true,
        'nome', initcap(split_part(v_cli.nome, ' ', 1)),
        'loja', (select valor from configuracoes where chave = 'loja_nome'),
        'saldo', coalesce((select sum(pontos_restantes) from movimentos
                           where cliente_id = v_cli.id and pontos_restantes > 0 and expira_em >= v_hoje), 0),
        'a_vencer_30', coalesce((select sum(pontos_restantes) from movimentos
                                 where cliente_id = v_cli.id and pontos_restantes > 0
                                   and expira_em between v_hoje and v_hoje + 30), 0),
        'proximo_vencimento', (select min(expira_em) from movimentos
                               where cliente_id = v_cli.id and pontos_restantes > 0 and expira_em >= v_hoje),
        'modo', coalesce((select valor from configuracoes where chave = 'modo_resgate'), 'AMBOS'),
        'desconto_pontos', coalesce((select valor from configuracoes where chave = 'desconto_pontos'), '100'),
        'desconto_reais', coalesce((select valor from configuracoes where chave = 'desconto_reais'), '5.00'),
        'regra_reais', coalesce((select valor from configuracoes where chave = 'regra_reais'), '1.00'),
        'regra_pontos', coalesce((select valor from configuracoes where chave = 'regra_pontos'), '1'),
        'validade_dias', coalesce((select valor from configuracoes where chave = 'validade_dias'), '60'),
        'compra_minima', coalesce((select valor from configuracoes where chave = 'resgate_compra_minima'), '40.00'),
        'extrato', coalesce((select json_agg(x) from (
                        select criado_em, tipo, pontos, descricao, expira_em, estornado
                        from movimentos where cliente_id = v_cli.id
                        order by criado_em desc, id desc limit 20) x), '[]'::json),
        'premios', coalesce((select json_agg(p) from (
                        select nome, descricao, pontos,
                               case when imagem like 'http%' then imagem end as imagem
                        from premios where ativo and estoque <> 0
                        order by pontos limit 60) p), '[]'::json)
    );
end;
$$;
