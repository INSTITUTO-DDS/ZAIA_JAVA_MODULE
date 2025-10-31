package br.com.zaya.flow.events;

import br.com.sankhya.extensions.flow.ContextoTarefa;
import br.com.sankhya.extensions.flow.TarefaJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.util.JapeSessionContext;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.facades.AdiantamentoEmprestimoSPBean;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.ws.ServiceContext;
import br.com.sankhya.ws.transformer.json.Json2XMLParser;
import com.google.gson.JsonObject;
import com.sankhya.util.JsonUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

public class GerarAdiantamento implements TarefaJava {
    private final EntityFacade dwFacade = EntityFacadeFactory.getDWFFacade();
    private final JdbcWrapper jdbc = null;

    public static String montarJsonParamsDoResultSet(ResultSet rs) throws Exception {

        String Json = null;
        while (rs.next()) {
            System.out.println("montando rs..." + rs.toString());
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            String dtNeg = sdf.format(rs.getTimestamp("DTNEG"));
            String dtVenc = sdf.format(rs.getTimestamp("DTVENC"));
            String valorAdiantamento = String.format("%,.2f", rs.getDouble("VLRDESDOB")).replace(".", "#").replace(",", ".").replace("#", ",");

            // Monta a string formatada
            System.out.println("montarJson - Executando...");
            Json = String.format(
                    "{\n" +
                            "  \"params\": {\n" +
                            "    \"despesa\": {\n" +
                            "      \"codigoEmpresa\": %d,\n" +
                            "      \"codigoParceiro\": %d,\n" +
                            "      \"dataNegociacao\": \"%s\",\n" +
                            "      \"vencimentoDespesa\": \"%s\",\n" +
                            "      \"valorAdiantamento\": \"%s\",\n" +
                            "      \"codigoTipoOperacao\": %d,\n" +
                            "      \"codigoTipoTitulo\": %d,\n" +
                            "      \"codigoConta\": %d,\n" +
                            "      \"codigoNatureza\": %d,\n" +
                            "      \"salvarCrNatConta\": false,\n" +
                            "      \"codigoProjeto\": %d,\n" +
                            "      \"codigoCentroCusto\": %d,\n" +
                            "      \"dataEntradaSaida\": \"%s\",\n" +
                            "      \"numeroNota\": \"\",\n" +
                            "      \"codigoFuncionario\": \"\",\n" +
                            "      \"historico\": null,\n" +
                            "      \"codigoVeiculo\": \"\",\n" +
                            "      \"ordemCarga\": \"\"\n" +
                            "    },\n" +
                            "    \"receitas\": {\n" +
                            "      \"numeroParcelas\": 1,\n" +
                            "      \"taxaJuros\": \"0,00\",\n" +
                            "      \"tipoParcelamento\": \"0\",\n" +
                            "      \"incPrazoPrimeiraParcela\": false,\n" +
                            "      \"tipoJuros\": \"EMBUTIDO\",\n" +
                            "      \"frequencia\": \"0\",\n" +
                            "      \"tipoDataBase\": \"0\",\n" +
                            "      \"dataBase\": \"%s\",\n" +
                            "      \"codigoTipoTitulo\": %d,\n" +
                            "      \"codigoNatureza\": %d,\n" +
                            "      \"codigoTipoOperacao\": %d\n" +
                            "    },\n" +
                            "    \"comRepasseFornecedor\": false\n" +
                            "  }\n" +
                            "}",

                    rs.getInt("CODEMP"),
                    rs.getInt("CODPARC"),
                    dtNeg,
                    dtVenc,
                    valorAdiantamento,
                    rs.getInt("CODTIPOPER"),
                    rs.getInt("CODTIPTITPAD"),
                    rs.getInt("CODCTABCOINT"),
                    rs.getInt("CODNAT"),
                    2040005,
                    rs.getInt("CODCENCUS"),
                    dtNeg,
                    dtNeg,
                    rs.getInt("CODTIPTITPAD"),
                    810201,
                    rs.getInt("CODTIPOPER")
            );
        }
        return Json;
    }

    public static String montarStringParcelasJson(ResultSet rs) throws Exception {

        String json = null;
        while (rs.next()) {
            System.out.println("montando rs..." + rs.toString());
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            String dtNeg = sdf.format(rs.getTimestamp("DTNEG"));
            String dtVenc = sdf.format(rs.getTimestamp("DTVENC"));
            String dtEntSai = sdf.format(rs.getTimestamp("DTNEG"));
            String valorDesdob = String.format("%.0f", rs.getDouble("VLRDESDOB"));

            json = String.format(
                    "{\n" +
                            "  \"parcelas\": {\n" +
                            "    \"impressao\": \"ADIANTEMP\",\n" +
                            "    \"parcela\": [\n" +
                            "      {\n" +
                            "        \"CODEMP\": {\"$\": %d},\n" +
                            "        \"AD_IDPROCESSO\": {\"$\": %d},\n" +
                            "        \"CODPARC\": {\"$\": %d},\n" +
                            "        \"CODCONTATO\": {\"$\": \"\"},\n" +
                            "        \"TIMIMOVEL\": {\"$\": \"\"},\n" +
                            "        \"CODTIPOPER\": {\"$\": %d},\n" +
                            "        \"CODTIPTIT\": {\"$\": 26},\n" +
                            "        \"CODNAT\": {\"$\": %d},\n" +
                            "        \"VLRDESDOB\": {\"$\": %s},\n" +
                            "        \"CODCENCUS\": {\"$\": %d},\n" +
                            "        \"CODCTABCOINT\": {\"$\": 195},\n" +
                            "        \"CODVEICULO\": {\"$\": 0},\n" +
                            "        \"CODPROJ\": {\"$\": 2040005},\n" +
                            "        \"RECDESP\": {\"$\": -1},\n" +
                            "        \"VLRJUROEMBUT\": {\"$\": \"\"},\n" +
                            "        \"VLRJURONEGOC\": {\"$\": \"\"},\n" +
                            "        \"VLRMULTA\": {\"$\": 0},\n" +
                            "        \"ORDEMCARGA\": {\"$\": 0},\n" +
                            "        \"CODFUNC\": {\"$\": \"\"},\n" +
                            "        \"CODBCO\": {\"$\": 1},\n" +
                            "        \"DESDOBRAMENTO\": {\"$\": \"0\"},\n" +
                            "        \"NUMNOTA\": {\"$\": \"\"},\n" +
                            "        \"PROVISAO\": {\"$\": \"N\"},\n" +
                            "        \"ORIGEM\": {\"$\": \"F\"},\n" +
                            "        \"DESDOBDUPL\": {\"$\": \"ZZ\"},\n" +
                            "        \"TIPMARCCHEQ\": {\"$\": \"I\"},\n" +
                            "        \"TIPMULTA\": {\"$\": \"1\"},\n" +
                            "        \"TIPJURO\": {\"$\": \"1\"},\n" +
                            "        \"DTNEG\": {\"$\": \"%s\"},\n" +
                            "        \"DTVENC\": {\"$\": \"%s\"},\n" +
                            "        \"DTVENCINIC\": {\"$\": \"%s\"},\n" +
                            "        \"DTENTSAI\": {\"$\": \"%s\"}\n" +
                            "      },\n" +
                            "      {\n" +
                            "        \"CODEMP\": {\"$\": %d},\n" +
                            "        \"AD_IDPROCESSO\": {\"$\": %d},\n" +
                            "        \"CODPARC\": {\"$\": %d},\n" +
                            "        \"CODCONTATO\": {\"$\": \"\"},\n" +
                            "        \"TIMIMOVEL\": {\"$\": \"\"},\n" +
                            "        \"CODTIPOPER\": {\"$\": %d},\n" +
                            "        \"CODTIPTIT\": {\"$\": 26},\n" +
                            "        \"CODNAT\": {\"$\": 810201},\n" +
                            "        \"VLRDESDOB\": {\"$\": %s},\n" +
                            "        \"CODCENCUS\": {\"$\": %d},\n" +
                            "        \"CODCTABCOINT\": {\"$\": 195},\n" +
                            "        \"CODVEICULO\": {\"$\": 0},\n" +
                            "        \"CODPROJ\": {\"$\": 2040005},\n" +
                            "        \"RECDESP\": {\"$\": 1},\n" +
                            "        \"VLRJUROEMBUT\": {\"$\": 0},\n" +
                            "        \"VLRJURONEGOC\": {\"$\": 0},\n" +
                            "        \"VLRMULTA\": {\"$\": 0},\n" +
                            "        \"ORDEMCARGA\": {\"$\": 0},\n" +
                            "        \"CODFUNC\": {\"$\": \"\"},\n" +
                            "        \"CODBCO\": {\"$\": 1},\n" +
                            "        \"DESDOBRAMENTO\": {\"$\": \"1\"},\n" +
                            "        \"NUMNOTA\": {\"$\": \"\"},\n" +
                            "        \"PROVISAO\": {\"$\": \"N\"},\n" +
                            "        \"ORIGEM\": {\"$\": \"F\"},\n" +
                            "        \"DESDOBDUPL\": {\"$\": \"ZZ\"},\n" +
                            "        \"TIPMARCCHEQ\": {\"$\": \"I\"},\n" +
                            "        \"TIPMULTA\": {\"$\": \"1\"},\n" +
                            "        \"TIPJURO\": {\"$\": \"1\"},\n" +
                            "        \"DTNEG\": {\"$\": \"%s\"},\n" +
                            "        \"DTVENC\": {\"$\": \"%s\"},\n" +
                            "        \"DTVENCINIC\": {\"$\": \"%s\"},\n" +
                            "        \"DTENTSAI\": {\"$\": \"%s\"}\n" +
                            "      }\n" +
                            "    ]\n" +
                            "  }\n" +
                            "}",

                    rs.getInt("CODEMP"),
                    rs.getInt("IDPROCESSO"),
                    rs.getInt("CODPARC"),
                    rs.getInt("CODTIPOPER"),
                    rs.getInt("CODNAT"),
                    valorDesdob,
                    rs.getInt("CODCENCUS"),
                    dtNeg,
                    dtVenc,
                    dtVenc,
                    dtEntSai,

                    rs.getInt("CODEMP"),
                    rs.getInt("IDPROCESSO"),
                    rs.getInt("CODPARC"),
                    rs.getInt("CODTIPOPER"),
                    valorDesdob,
                    rs.getInt("CODCENCUS"),
                    dtNeg,
                    dtVenc,
                    dtVenc,
                    dtEntSai
            );
        }
        System.out.println("Json montado:"+json.toString());
        return json;
    }

    @Override
    public void executar(ContextoTarefa ctx) throws Exception {
        BigDecimal idProcesso = (BigDecimal) ctx.getIdInstanceProcesso();
        BigDecimal idTarefa = (BigDecimal) ctx.getIdInstanceTarefa();
        BigDecimal codUsuInc = ctx.getUsuarioInclusao();
        BigDecimal codUsuLogado = ctx.getUsuarioLogado();
        ResultSet resultado = null;

        try {
            System.out.println("GerarAdiantamento. Executando...");
            AdiantamentoEmprestimoSPBean bean = new AdiantamentoEmprestimoSPBean();

            AuthenticationInfo auth = new AuthenticationInfo("SUP", BigDecimal.ZERO, BigDecimal.ZERO, 0);
            auth.makeCurrent();
            ServiceContext sctx = new ServiceContext(null);
            sctx.setAutentication(auth);
            sctx.makeCurrent();
            JapeSessionContext.putProperty("usuario_logado", codUsuLogado);
            JapeSession.putProperty("usuario_logado", codUsuLogado);

            resultado = rsDados(idProcesso, jdbc);
            String params = montarJsonParamsDoResultSet(resultado);
            resultado = rsDados(idProcesso, jdbc);
            String parcelas = montarStringParcelasJson(resultado);
            JsonObject param = JsonUtils.convertStringToJsonObject(params);
            JsonObject parcela = JsonUtils.convertStringToJsonObject(parcelas);
            System.out.println("Json: "+param);
            System.out.println("JsonParcela: "+parcela);
            sctx.setJsonRequestBody(param);
            sctx.setRequestBody(Json2XMLParser.jsonToElement("param", sctx.getJsonRequestBody()));

            bean.gerarAdiantamentoEmprestimo(sctx);
            sctx.getJsonRequestBody().remove("param");
            sctx.setJsonRequestBody(parcela);
            sctx.setRequestBody(Json2XMLParser.jsonToElement("parcelas", sctx.getJsonRequestBody()));
            bean.salvarParcelamento(sctx);
        }catch(Exception e){
            e.printStackTrace();
            throw new Exception("Erro ao gerar adiantamento."+e.getMessage());
        }
    }

    public ResultSet rsDados(BigDecimal idInstPrn, JdbcWrapper jdbc) throws SQLException {
        jdbc = dwFacade.getJdbcWrapper();
        jdbc.openSession();

        try {
            System.out.println("RsDados. Executando...");
            NativeSql sql = new NativeSql(jdbc);

            sql.setReuseStatements(false);
            sql.loadSql(GerarAdiantamento.class, "queDados.sql");
            sql.cleanParameters();
            sql.setNamedParameter("IDPROCESSO", idInstPrn);

            ResultSet rs = sql.executeQuery();
            return rs;
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Erro ao executar SQL");
        }
    }
}