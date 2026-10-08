package ctosim;

import java.util.Collections;
import java.util.List;
import org.javarosa.core.services.IPropertyManager;
import org.javarosa.core.services.properties.IPropertyRules;

/**
 * Device properties served to the engine's {@code jr:preload="property"} fields. The
 * engine's default property manager needs a storage backend that a simulation does not
 * have; this one answers from constants instead and ignores writes.
 */
final class SimulatedProperties implements IPropertyManager {

    static final String USERNAME = "simulated";

    @Override
    public String getSingularProperty(String name) {
        // Any other property (deviceid, subscriberid, ...) is unavailable: the field stays empty.
        return "username".equals(name) ? USERNAME : null;
    }

    @Override
    public List<String> getProperty(String name) {
        String value = getSingularProperty(name);
        return value == null ? null : List.of(value);
    }

    @Override
    public void setProperty(String name, String value) {
    }

    @Override
    public void setProperty(String name, List<String> values) {
    }

    @Override
    public void addRules(IPropertyRules rules) {
    }

    @Override
    public List<IPropertyRules> getRules() {
        return Collections.emptyList();
    }
}
