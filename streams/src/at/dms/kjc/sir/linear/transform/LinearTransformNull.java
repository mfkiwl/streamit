package at.dms.kjc.sir.linear.transform;

import at.dms.kjc.sir.linear.*;

/**
 * Represents a null transform -- eg no transform to apply.
 * This class represents the graceful way to not perform a 
 * transform on an ill-defined input. For example, if a splitjoin
 * is unschedulable, we can't perform a combination so a Null
 * transform is returned.<br>
 *
 * $Id: LinearTransformNull.java,v 1.4 2006-01-25 17:02:01 thies Exp $
 **/
public class LinearTransformNull extends LinearTransform {
    /** The reason that no transformation could be applied. **/
    String reason;

    /**
     * Creates a new LinearTransformNull. The reason that no
     * transform was possible specified in the string r.
     **/
    public LinearTransformNull(String r) {
        this.reason = r;
    }
    
    public LinearFilterRepresentation transform() throws NoTransformPossibleException {
        throw new NoTransformPossibleException(reason);
    }
}
