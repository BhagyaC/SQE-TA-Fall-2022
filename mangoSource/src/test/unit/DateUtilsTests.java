package test.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.mango.Common;
import com.serotonin.mango.util.DateUtils;

public class DateUtilsTests {
	
	//time units in milis
	private long day = 86400000;

    @Test
	@DisplayName("minus given milis")
	void minusLong() {
		assertEquals(
			DateUtils.minus(11000, Common.TimePeriods.MILLISECONDS, 1000), 
			10000
		);

		assertEquals(
			DateUtils.minus(5 * day, Common.TimePeriods.DAYS, 3), 
			day * 2
		);

		assertEquals(
			DateUtils.minus(8 * day, Common.TimePeriods.WEEKS, 1), 
			day
		);
	}
}