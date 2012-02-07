package de.hpi.bpt.process.epc;

import de.hpi.bpt.process.IActivity;

/**
 * EPC function interface
 *
 * @author Artem Polyvyanyy
 */
public interface IFunction extends IActivity {
	
	/**
	 * Get function duration in milliseconds
	 * @return Function duration
	 */
	public long getDuration();
	
	/**
	 * Set function duration
	 * @param duration Duration in milliseconds
	 */
	public void setDuration(long duration);
}
