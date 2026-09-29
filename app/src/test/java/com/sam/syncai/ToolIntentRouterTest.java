package com.sam.syncai;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ToolIntentRouterTest {
    @Test
    public void timeQueryRoutesDeterministically() {
        List<ToolCall> calls = ToolIntentRouter.parseAll("what time is it");
        assertEquals(1, calls.size());
        assertEquals("time", calls.get(0).name);
        assertEquals("time", calls.get(0).arguments.get("query"));
    }

    @Test
    public void directFlashlightCommandDoesNotNeedLlm() {
        List<ToolCall> calls = ToolIntentRouter.parseAll("yo, switch flashlight off");
        assertEquals(1, calls.size());
        assertEquals("flashlight", calls.get(0).name);
        assertEquals("false", calls.get(0).arguments.get("enabled"));
    }

    @Test
    public void contextualFlashlightUsesLastSuccessfulTool() {
        ToolIntentRouter.clearContext();
        ToolIntentRouter.rememberSuccessfulTool(
                new ToolCall("flashlight", java.util.Collections.singletonMap("enabled", "true")));
        ToolCall call = ToolIntentRouter.parse("turn it off");
        assertNotNull(call);
        assertEquals("flashlight", call.name);
        assertEquals("false", call.arguments.get("enabled"));
        ToolIntentRouter.clearContext();
    }

    @Test
    public void compoundAlarmRequestProducesTwoTools() {
        List<ToolCall> calls = ToolIntentRouter.parseAll(
                "set an alarm for 2:45 am and another one for 10:30 pm titled work");
        assertEquals(2, calls.size());
        assertEquals("set_alarm", calls.get(0).name);
        assertEquals("2", calls.get(0).arguments.get("hour"));
        assertEquals("22", calls.get(1).arguments.get("hour"));
        assertEquals("30", calls.get(1).arguments.get("minute"));
        assertEquals("work", calls.get(1).arguments.get("message"));
    }

    @Test
    public void calculatorNaturalLanguageRoutesDirectly() {
        List<ToolCall> calls = ToolIntentRouter.parseAll("what is 10 percent of 200");
        assertEquals(1, calls.size());
        assertEquals("calculator", calls.get(0).name);
        assertTrue(calls.get(0).arguments.get("expression").contains("*0.01*"));
    }

    @Test
    public void ordinaryConversationFallsThrough() {
        assertNull(ToolIntentRouter.parse("how ya doing today"));
    }
}
