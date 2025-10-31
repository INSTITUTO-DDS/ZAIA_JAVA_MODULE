package br.com.zaya.flow.actions;

import java.io.UnsupportedEncodingException;

import org.json.JSONException;
import org.json.JSONObject;

public class CampoAnexoBlob {
    private byte[] dadosArquivo;
    private byte[] informacaoArquivo;
    private byte[] arquivo;
    private String nome;
    private String extensao;

    public CampoAnexoBlob(byte[] dadosArquivo, String nomeArquivo, long tamanhoArquivo, String tipoArquivo, String ultimaModificacao) throws UnsupportedEncodingException {
        this.dadosArquivo = dadosArquivo;

        JSONObject informacaoArquivoJson = new JSONObject();
        try {
            informacaoArquivoJson.put("name", nomeArquivo);
            informacaoArquivoJson.put("size", tamanhoArquivo);
            informacaoArquivoJson.put("type", tipoArquivo);
            informacaoArquivoJson.put("lastModifiedDate", ultimaModificacao);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        String informacaoArquivoString = "__start_fileinformation__" + informacaoArquivoJson.toString() + "__end_fileinformation__";
        byte[] informacaoArquivoBytes = informacaoArquivoString.getBytes("UTF-8");

        byte[] arquivoFinal = new byte[informacaoArquivoBytes.length + dadosArquivo.length];
        System.arraycopy(informacaoArquivoBytes, 0, arquivoFinal, 0, informacaoArquivoBytes.length);
        System.arraycopy(dadosArquivo, 0, arquivoFinal, informacaoArquivoBytes.length, dadosArquivo.length);

        this.arquivo = arquivoFinal;

        this.nome = nomeArquivo;
        this.extensao = nomeArquivo.substring(nomeArquivo.lastIndexOf(".") + 1).toLowerCase();
    }

    public byte[] getArquivo() {
        return arquivo;
    }
}

