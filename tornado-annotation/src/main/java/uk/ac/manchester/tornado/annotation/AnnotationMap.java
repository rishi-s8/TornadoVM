package uk.ac.manchester.tornado.annotation;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class AnnotationMap implements Serializable {
    HashMap<String, ArrayList<ParallelAnnotation>> annotations = new HashMap<>();

    public void addAnnotation(String signature, Integer start, Integer length, Integer index) {
        if (!annotations.containsKey(signature)) {
            annotations.put(signature, new ArrayList<ParallelAnnotation>());
        }
        ArrayList<ParallelAnnotation> annotation = annotations.get(signature);
        ParallelAnnotation p = new ParallelAnnotation(start, length, index);
        annotation.add(p);
    }
}
