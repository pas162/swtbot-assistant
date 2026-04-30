package com.renesas.swtbot.assistant.jira.model;

import java.util.List;

public class TicketData {

    private String key;
    private String name;
    private String description;
    private String precondition;
    private List<TestStep> steps;

    public static class TestStep {
        private int index;
        private String description;
        private String expectedResult;

        public TestStep(int index, String description, String expectedResult) {
            this.index = index;
            this.description = description;
            this.expectedResult = expectedResult;
        }

        public int getIndex() { return index; }
        public String getDescription() { return description; }
        public String getExpectedResult() { return expectedResult; }
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPrecondition() { return precondition; }
    public void setPrecondition(String precondition) { this.precondition = precondition; }

    public List<TestStep> getSteps() { return steps; }
    public void setSteps(List<TestStep> steps) { this.steps = steps; }
}
