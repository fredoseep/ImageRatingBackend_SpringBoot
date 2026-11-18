package com.example.rating;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:api;DB_CLOSE_DELAY=-1","rating.admin-key=test-admin"})
@AutoConfigureMockMvc
class ApiTest {
    @Autowired MockMvc mvc;
    String upload="{\"request_id\":\"api-test-123\",\"description\":\"test\",\"image_base64\":\"iVBORw0KGgo=\"}";
    @Test void adminRequiresKeyButMobileIsPublic()throws Exception{
        mvc.perform(get("/api/admin/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/tasks").header("Authorization","Bearer test-client")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/tasks").header("Authorization","Bearer test-admin")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk());
    }
    @Test void uploadAndQueryContract()throws Exception{
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(upload)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("fallback")).andExpect(jsonPath("$.reason").value("operator_offline")).andExpect(jsonPath("$.image_base64").doesNotExist());
    }
    @Test void malformedBase64AndMissingLengthRejected()throws Exception{
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(upload.replace("iVBORw0KGgo=","%%%"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isPayloadTooLarge());
    }
}
