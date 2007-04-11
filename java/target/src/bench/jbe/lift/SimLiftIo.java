package jbe.lift;

public class SimLiftIo {

	public static final int IO_BASE = 0xffffff80;

	// TAL, baseio (scio_baseio.vhd)
	public static final int IO_IN = IO_BASE+0x40+0;
	public static final int IO_LED = IO_BASE+0x40+0;
	public static final int IO_OUT = IO_BASE+0x40+1;
	public static final int IO_ADC1 = IO_BASE+0x40+1;
	public static final int IO_ADC2 = IO_BASE+0x40+2;
	public static final int IO_ADC3 = IO_BASE+0x40+3;
	
	/**
	 * Generate some dummy values to avoid optimizing
	 * this method away.
	 */
	static int cnt;
	
	public static int rd(int addr) {
		return cnt+addr;
	}
	public static void wr(int val, int addr) {
		cnt += val + addr;
	}

}
