package projet;

import com.advisora.Model.projet.Top10CompanyItem;
import com.advisora.Model.projet.Top10Response;
import com.advisora.Services.projet.Top10AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class test_top10_ai_service {

    @Test
    void parse_nominal_json_and_normalize_to_ranked_top10() throws Exception {
        Top10AiService service = new Top10AiService();
        ObjectMapper mapper = new ObjectMapper();

        Top10Response modelOutput = sampleResponse(10);
        String assistantJson = mapper.writeValueAsString(modelOutput);

        String apiJson = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + mapper.writeValueAsString(assistantJson) + "}]}}]}";

        Method extract = Top10AiService.class.getDeclaredMethod("extractAssistantJson", String.class);
        extract.setAccessible(true);
        Method parse = Top10AiService.class.getDeclaredMethod("parseAssistantJson", String.class);
        parse.setAccessible(true);
        Method normalize = Top10AiService.class.getDeclaredMethod("normalizeAndValidate", Top10Response.class);
        normalize.setAccessible(true);

        String extracted = (String) extract.invoke(service, apiJson);
        Top10Response parsed = (Top10Response) parse.invoke(service, extracted);
        Top10Response normalized = (Top10Response) normalize.invoke(service, parsed);

        assertEquals(10, normalized.getTop10().size());
        assertEquals(1, normalized.getTop10().get(0).getRank());
        assertTrue(normalized.getTop10().get(0).getRevenueUsdBillions() >= normalized.getTop10().get(9).getRevenueUsdBillions());
    }

    @Test
    void reject_incomplete_top10() throws Exception {
        Top10AiService service = new Top10AiService();
        Method normalize = Top10AiService.class.getDeclaredMethod("normalizeAndValidate", Top10Response.class);
        normalize.setAccessible(true);

        Top10Response shortResponse = sampleResponse(4);
        Exception ex = assertThrows(Exception.class, () -> normalize.invoke(service, shortResponse));
        assertTrue(ex.getMessage() != null);
    }

    @Test
    void rerank_after_sorting_desc_by_revenue() throws Exception {
        Top10AiService service = new Top10AiService();
        Method normalize = Top10AiService.class.getDeclaredMethod("normalizeAndValidate", Top10Response.class);
        normalize.setAccessible(true);

        Top10Response response = sampleResponse(10);
        response.getTop10().get(0).setRevenueUsdBillions(1.0);
        response.getTop10().get(9).setRevenueUsdBillions(999.0);

        Top10Response out = (Top10Response) normalize.invoke(service, response);
        assertEquals(999.0, out.getTop10().get(0).getRevenueUsdBillions(), 0.0001);
        assertEquals(1, out.getTop10().get(0).getRank());
        assertEquals(10, out.getTop10().get(9).getRank());
    }

    @Test
    void missing_api_key_fails_fast() throws Exception {
        Constructor<Top10AiService> ctor = Top10AiService.class.getDeclaredConstructor(
                HttpClient.class, ObjectMapper.class, Supplier.class);
        ctor.setAccessible(true);
        Top10AiService service = ctor.newInstance(HttpClient.newHttpClient(), new ObjectMapper(), (Supplier<String>) () -> " ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.generateTop10("streaming"));
        assertTrue(ex.getMessage().contains("GEMINI_API_KEY"));
    }

    @Test
    void reject_invalid_rank_values() throws Exception {
        Top10AiService service = new Top10AiService();
        Method normalize = Top10AiService.class.getDeclaredMethod("normalizeAndValidate", Top10Response.class);
        normalize.setAccessible(true);

        Top10Response response = sampleResponse(10);
        response.getTop10().get(0).setRank(0);

        Exception ex = assertThrows(Exception.class, () -> normalize.invoke(service, response));
        assertTrue(ex.getMessage() != null);
    }

    private Top10Response sampleResponse(int size) {
        Top10Response response = new Top10Response();
        response.setCategory("Streaming");
        response.setDisclaimer("x");

        List<Top10CompanyItem> rows = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Top10CompanyItem item = new Top10CompanyItem();
            item.setRank(i + 1);
            item.setName("Company " + (i + 1));
            item.setRevenueUsdBillions(100.0 - i);
            item.setYear(2024);
            item.setDescription("Description " + (i + 1));
            rows.add(item);
        }
        response.setTop10(rows);
        return response;
    }
}
