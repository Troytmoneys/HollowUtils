package io.github.troytmoneys.hollowutils;

import io.github.troytmoneys.hollowutils.fixture.HiddenServiceFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MultiverseBridgeTest {
    @Test void invokesPublicInterfaceOnHiddenImplementationAndHiddenSuperclass() {
        Object service = HiddenServiceFactory.create();
        assertEquals("group:survival", MultiverseBridge.invoke(service,"read","survival"));
        assertNull(MultiverseBridge.invoke(service,"save",true));
    }
    @Test void preservesUnderlyingApiFailureForDiagnosis() {
        var error = assertThrows(IllegalStateException.class, () -> MultiverseBridge.invoke(HiddenServiceFactory.create(),"save",false));
        assertInstanceOf(IllegalArgumentException.class,error.getCause());
        assertTrue(error.getMessage().contains("Save rejected"));
    }
    @Test void missingApiMethodIsReportedWithoutBypassingAccessControls() {
        var error = assertThrows(IllegalStateException.class, () -> MultiverseBridge.invoke(HiddenServiceFactory.create(),"missing"));
        assertTrue(error.getMessage().contains("Incompatible Multiverse API"));
    }
}
