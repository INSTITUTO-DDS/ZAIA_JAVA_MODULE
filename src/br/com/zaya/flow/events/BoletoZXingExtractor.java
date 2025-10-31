package br.com.zaya.flow.events;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class BoletoZXingExtractor {

    public static String decodeBarcodeFromPdf(byte[] pdfBytes) throws Exception {
        PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes));
        try {
            @SuppressWarnings("unchecked")
            List<PDPage> pages = (List<PDPage>) document.getDocumentCatalog().getAllPages();
            PDPage first = pages.get(0);
            BufferedImage pageImage = first.convertToImage(
                    BufferedImage.TYPE_INT_RGB, (int) 300f
            );

            int margin = 20;
            int cropHeight = 100;
            int x = margin;
            int y = pageImage.getHeight() - cropHeight - margin;
            int w = pageImage.getWidth() - 2 * margin;
            BufferedImage region = pageImage.getSubimage(x, y, w, cropHeight);

            Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.ITF));

            MultiFormatReader reader = new MultiFormatReader();
            reader.setHints(hints);

            LuminanceSource src = new BufferedImageLuminanceSource(region);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(src));

            try {
                return reader.decode(bitmap).getText();
            } catch (NotFoundException e) {
                BinaryBitmap inverted = new BinaryBitmap(
                        new HybridBinarizer(new InvertedLuminanceSource(src))
                );
                return reader.decode(inverted).getText();
            }
        } finally {
            document.close();
        }
    }
}