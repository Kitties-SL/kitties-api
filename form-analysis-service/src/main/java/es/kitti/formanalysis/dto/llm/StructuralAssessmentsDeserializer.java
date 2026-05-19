package es.kitti.formanalysis.dto.llm;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StructuralAssessmentsDeserializer extends StdDeserializer<List<StructuralAssessment>> {

    public StructuralAssessmentsDeserializer() {
        super(List.class);
    }

    @Override
    public List<StructuralAssessment> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_ARRAY) {
            var assessments = new ArrayList<StructuralAssessment>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                assessments.add(ctxt.readValue(p, StructuralAssessment.class));
            }
            return assessments;
        }
        p.skipChildren();
        return List.of();
    }
}
