/*
 * Created on 12.12.2005
 *
 */
package dsp;

import com.jopdesign.sys.Const;
import com.jopdesign.sys.Native;

public class TestPlay extends AC97 {

	public static void run() {
		
		int left, right, i, j, v1, v2;
		
		for (i=0; i<10; ++i) {
			// flush input FIFOs
			rd(ICH0);
			rd(ICH1);
			rd(INTS);
		}
		
		i = 0;
		
		for (;;) {
			// busy wait for input samples
			for (;;) {
				int status =Native.rdMem(Const.WB_AC97+INTS);
				if ((status&0x2d00000)!=0) break;
			}
			left = Native.rdMem(Const.WB_AC97+ICH0);
			right = Native.rdMem(Const.WB_AC97+ICH1);
//			if ((Native.rdMem(Const.WB_AC97+INTS)&0x1200000)!=0) {
//				continue;
//			}
			

			i = (i+1) & 0xffff;
			j = i;
			if (j>0x7fff) {
				j = 0xffff-i;
			}
			
			v1 = v2 = (i & 0x7f)<<5;
			left = (v2<<16)+v1; 
			

			v1 = v2 = (i & 0x3f)<<5;
			right = (v2<<16)+v1; 

			Native.wrMem(left, Const.WB_AC97+OCH0);
			Native.wrMem(right, Const.WB_AC97+OCH1);
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		init();
		run();

	}

}
