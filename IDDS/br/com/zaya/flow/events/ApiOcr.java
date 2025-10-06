package br.com.zaya.flow.events;

import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.util.Collection;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.jape.wrapper.fluid.FluidCreateVO;
import br.com.sankhya.jape.wrapper.fluid.FluidUpdateVO;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import br.com.zaya.flow.events.LinhaDigitavelExtractor;

public class ApiOcr {
    private static final int TIMEOUT_MS = 20_000;

    @SuppressWarnings("resource")
    public static String capturaCodigoBarras(byte[] pdfBytes, BigDecimal codUsu, BigDecimal idInstPrn) {

        String apiUrl = "https://api.ocr.space/parse/image";
        String apiKey = "K83176762288969";
        String codBarras = null;

        if (pdfBytes == null) {
            registrarLog("Erro", "Arquivo PDF nulo. Não foi possível processar.", null, "Boleto", codUsu, idInstPrn);
        }

        String boundary = "----BoundaryString";
        HttpURLConnection connection = null;

        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());

            writeFormField(outputStream, boundary, "language", "eng");
            writeFormField(outputStream, boundary, "isOverlayRequired", "false");
            writeFormField(outputStream, boundary, "iscreatesearchablepdf", "false");
            writeFormField(outputStream, boundary, "issearchablepdfhidetextlayer", "false");
            writeFormField(outputStream, boundary, "filetype", "pdf");
            writeFormField(outputStream, boundary, "scale", "true");
            writeFormField(outputStream, boundary, "OCREngine", "2");

            writeFileField(outputStream, boundary, "file", "arquivo.pdf", "application/pdf", pdfBytes);

            outputStream.writeBytes("\r\n--" + boundary + "--\r\n");
            outputStream.flush();
            outputStream.close();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {

                InputStream responseStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, "UTF-8"));
                String response = reader.lines().reduce("", (acc, line) -> acc + line);

                try {
                    JSONObject jsonObject = new JSONObject(response);
                    JSONArray parsedResults = jsonObject.getJSONArray("ParsedResults");
                    String parsedText = parsedResults.getJSONObject(0).getString("ParsedText");

                    if (parsedText == null || parsedText.trim().isEmpty()) {
                        registrarLog("Erro na leitura", "Texto retornado da API vazio ou nulo. JSON: " + response, null, "Boleto", codUsu, idInstPrn);
                    } else {

                        try {
                            codBarras = LinhaDigitavelExtractor.extraiCodBarras(parsedText);
                            System.out.println("ApiOCR - Código extraído: " + codBarras);

                            if (codBarras == null || codBarras.trim().isEmpty()) {
                                registrarLog("Erro na extração - APIOcr", "Não foi possível extrair código de barras do texto. Texto OCR: " + parsedText, null, "Boleto", codUsu, idInstPrn);
                            } else {
                                registrarLog("Sucesso Extração", "Código de barras extraído com sucesso. Texto OCR: " + parsedText, codBarras, "Boleto", codUsu, idInstPrn);
                            }

                        } catch (Exception e) {
                            registrarLog("Erro na extração", "Falha ao extrair código de barras. Erro: " + e.getMessage(), null, "Boleto", codUsu, idInstPrn);
                            e.printStackTrace();
                        }
                    }

                } catch (Exception e) {
                    registrarLog("Erro na leitura do JSON", "Erro ao processar JSON de retorno da API. Erro: " + e.getMessage() + ". JSON: " + response, null, "Boleto", codUsu, idInstPrn);
                    e.printStackTrace();
                }

            } else {
                registrarLog("Erro na chamada da API", "Código HTTP: " + responseCode, null, "Boleto", codUsu, idInstPrn);
                System.err.println("Erro na requisição: Código HTTP " + responseCode);
            }

        } catch (Exception e) {
            registrarLog("Erro Geral", e.getMessage(), null, "Boleto", codUsu, idInstPrn);
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return codBarras;
    }

    private static void writeFormField(DataOutputStream outputStream, String boundary, String name, String value)
            throws IOException {
        outputStream.writeBytes("--" + boundary + "\r\n");
        outputStream.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        outputStream.writeBytes(value + "\r\n");
    }

    private static void writeFileField(DataOutputStream outputStream, String boundary, String fieldName,
                                       String fileName, String fileType, byte[] fileData) throws IOException {
        outputStream.writeBytes("--" + boundary + "\r\n");
        outputStream.writeBytes(
                "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n");
        outputStream.writeBytes("Content-Type: " + fileType + "\r\n\r\n");
        outputStream.write(fileData);
        outputStream.writeBytes("\r\n");
    }

    public static void registrarLog(String status, String mensagem, String codBarras, String nomeArq, BigDecimal codUsu, BigDecimal idProcesso) {
        JapeSession.SessionHandle hnd = null;
        try {
            hnd = JapeSession.open();
            EntityFacade ef = EntityFacadeFactory.getDWFFacade();

            JapeWrapper logDAO = JapeFactory.dao("AD_ZLOGAPIOCR");
            FluidCreateVO logVO = logDAO.create();
            logVO.set("DHLOG", new Timestamp(System.currentTimeMillis()));
            logVO.set("STATUS", status);
            logVO.set("MENSAGEM", mensagem.toCharArray());
            logVO.set("CODBARRAS", codBarras);
            logVO.set("NOMEARQ", nomeArq);
            logVO.set("CODUSU", codUsu);
            logVO.set("IDINSTPRN", idProcesso);

            logVO.save();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JapeSession.close(hnd);
        }
    }
}
