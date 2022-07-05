package jaerapps.functions.generateNames.util;

public class Assignment {
    private Person santa;
    private Person assignment;

    public Person getSanta() {
        return santa;
    }

    public Person getAssignment() {
        return assignment;
    }

    public Assignment(Person santa, Person assignment) {
        this.santa = santa;
        this.assignment = assignment;
    }
}
