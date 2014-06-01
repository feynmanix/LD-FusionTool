package cz.cuni.mff.odcleanstore.fusiontool.conflictresolution.util;

import cz.cuni.mff.odcleanstore.fusiontool.conflictresolution.UriMapping;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;

/**
 * TODO
 */
public class StatementMapper {
    private final UriMapping uriMapping;
    private final ValueFactory valueFactory;

    public StatementMapper(UriMapping uriMapping, ValueFactory valueFactory) {
        this.uriMapping = uriMapping;
        this.valueFactory = valueFactory;
    }

    public Statement mapStatement(Statement statement) {
        Resource subject = statement.getSubject();
        Resource mappedSubject = (Resource) mapUriNode(subject);
        URI predicate = statement.getPredicate();
        URI mappedPredicate = (URI) mapUriNode(predicate);
        Value object = statement.getObject();
        Value mappedObject = mapUriNode(object);

        // Intentionally !=
        if (subject != mappedSubject
                || predicate != mappedPredicate
                || object != mappedObject) {
            statement = valueFactory.createStatement(
                    mappedSubject,
                    mappedPredicate,
                    mappedObject,
                    statement.getContext());
        }
        return statement;
    }

    /**
     * If mapping contains an URI to map for the passed {@link org.openrdf.model.URI} returns a {@link org.openrdf.model.URI} with the mapped URI, otherwise returns
     * <code>value</code>.
     * @param value a {@link org.openrdf.model.Value} to apply mapping to
     * @return node with applied URI mapping
     */
    protected Value mapUriNode(Value value) {
        if (value instanceof Resource) {
            return uriMapping.mapResource((Resource) value);
        }
        return value;
    }
}
