package br.com.zaya.flow.events;

import br.com.sankhya.extensions.flow.ContextoEvento;
import br.com.sankhya.extensions.flow.EventoProcessoJava;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;

import java.util.Collection;

public class ValidaCodBarras implements EventoProcessoJava {
    @Override
    public void executar(ContextoEvento contexto) throws Exception {
        Object idProcesso = contexto.getIdInstanceProcesso();
        String mensagemErro = "";

        Collection<DynamicVO> fatConVo = JapeFactory.dao("AD_FATCONNOTAS").find("this.IDINSTPRN = ?", idProcesso);
        for(DynamicVO notasVO : fatConVo){
            String tipoPag = notasVO.asString("TIPOPAG");
            if(tipoPag.contains("B") && notasVO.asString("CODBARRAS") == null){
                mensagemErro = "<br><br>Não foi possível finalizar a tarefa:<br><br>Favor adicionar o código de barras do boleto no campo informado.";
                throw new Exception(mensagemErro);
            }
        }
    }
}
