package resource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FxmlEncodingTest {

    @Test
    void allFxmlFilesMustBeUtf8WithoutBom() throws IOException {
        Path root = Path.of("src/main/resources/views");
        List<String> filesWithBom = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".fxml"))
                    .forEach(p -> {
                        try {
                            byte[] bytes = Files.readAllBytes(p);
                            if (hasUtf8Bom(bytes)) {
                                filesWithBom.add(p.toString().replace('\\', '/'));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        assertTrue(filesWithBom.isEmpty(),
                "FXML files with UTF-8 BOM detected (remove BOM):\n - "
                        + String.join("\n - ", filesWithBom));
    }

    private boolean hasUtf8Bom(byte[] bytes) {
        return bytes != null
                && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }
}
