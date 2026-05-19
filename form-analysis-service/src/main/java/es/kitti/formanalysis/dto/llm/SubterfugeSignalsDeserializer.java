package es.kitti.formanalysis.dto.llm;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SubterfugeSignalsDeserializer extends StdDeserializer<List<String>> {

    public SubterfugeSignalsDeserializer() {
        super(List.class);
    }

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_ARRAY) {
            var signals = new ArrayList<String>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                signals.add(p.getText());
            }
            return signals;
        }
        // El modelo devolvió un string ("NONE", "none", o cualquier otro valor) → lista vacía
        p.skipChildren();
        return List.of();
    }
}