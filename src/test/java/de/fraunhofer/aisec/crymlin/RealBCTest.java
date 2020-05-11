
package de.fraunhofer.aisec.crymlin;

import de.fraunhofer.aisec.analysis.structures.Finding;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class RealBCTest extends AbstractMarkTest {

	@Test
	public void testSimple() throws Exception {
		// Just a very simple test to explore the graph
		Set<Finding> findings = performTest("real-examples/bc/rwedoff.Password-Manager/Main.java", "real-examples/bc/rwedoff.Password-Manager/");

		assertEquals(2, findings.stream().filter(Finding::isProblem).map(Finding::getOnfailIdentifier).distinct().count()); // MockWhen1 results in a finding.
	}

}
// currently broken/need investigating

// the problem seems to be that the entity references `this` which is not correct. See github-issue #5
// line -1: Verified Order: Cipher_Order