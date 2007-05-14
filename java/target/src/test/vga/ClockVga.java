package vga;

import com.jopdesign.sys.Const;
import com.jopdesign.sys.Native;
//
//	Clock.java
//

public class ClockVga {

	public static void main( String s[] ) {

		int addr = 0;
		int offset = 0x28000;
		boolean j = true;
		
		Native.wr(0, Const.IO_WD);		// make WD happy
		Native.wr(1, Const.IO_WD);
		Native.wr(0, Const.IO_WD);
		Native.wrMem(0x11111111, 0x28000); // first VGA Address	
		Native.wrMem(0x11111111, 0x3ffff); // last VGA Address
		
		for (int i=0x0; i<0x18000; i++)
		{
			addr = i + offset;
			if( i <= 1024)
			{
				Native.wrMem(0x22222222, addr); // rot
			}
			else if ( (i > 1024) && (i <= 2048) )
			{
				Native.wrMem(0xffffffff, addr); // gelb
			}
			else if ( (i > 2048) && (i <= 3072) ) 
			{
				Native.wrMem(0x33333333, addr); // hellrot
			}
			else if ( (i > 97280) && (i <= 98304) )
			{
				Native.wrMem(0xffffffff, addr); // gelb
			}
			else
			{
				Native.wrMem(0xCCCCCCCC, addr); // gr�n
				//Native.wrMem(0x00000000, addr); // schwarz
			}
				
			/*{
				if (j == true)
				{				
					Native.wrMem(0x33333333, addr); // orange
					j = false;
				}
				else
				{
					Native.wrMem(0xCCCCCCCC, addr); // gr�n
					j = true;
				}
			}
			else
			{
				if (j == true)
				{				
					Native.wrMem(0x22222222, addr); // rot
					j = false;
				}
				else
				{
					Native.wrMem(0xffffffff, addr); // gelb
					j = true;
				}
			}*/
		}
		
		time();
	}

	static void time() {

		int next;
		int h, m, s, ms;
		int startVga;
		int stopVga;
		int test;
		int start;
		int addr_line = 0;
		int offset_line = 0x29900;
		
		start = 0x28000;


		h = m = s = ms = 0;
		next = 0;
		s = -1;

		for (;;) {

			++ms;
			if (ms==1000) {
				ms = 0;
				++s;
				if (s==60) {
					s = 0;
					++m;
				}
				if (m==60) {
					m = 0;
					++h;
				}
				if (h==24) h = 0;
				print_02d(h);
				print_char(':');
				print_02d(m);
				print_char(':');
				print_02d(s);
				print_char(' ');
				print_char('S');
				print_char('T');
				print_char('A');
				print_char('R');
				print_char('T');
				print_char(':');
				print_char(' ');
				
				startVga = Native.rdMem(start);
				//print_02d(startVga);
				print_hex(startVga);
				
				print_char(' ');
				print_char('S');
				print_char('T');
				print_char('O');
				print_char('P');
				print_char(':');
				print_char(' ');
				stopVga = Native.rdMem(0x3ffff);
				//print_02d(stopVga);
				print_hex(stopVga);
				
				test = (s % 3);
				
				print_char(' ');
				print_char('E');
				print_char('R');
				print_char('G');
				print_char(':');
				print_char(' ');
				print_02d(test);
				print_char('\r');
				
				
				
			/*	for (int j=0; j<2048; j++)
				{
					addr_line = j + offset_line;
					
					switch (test)
					{
						case 0:
							if((j%128)<=42)
							{
								Native.wrMem(0x22222222, addr_line); // rot
							}
							else
							{
								Native.wrMem(0xCCCCCCCC, addr_line); // gr�n
							}
						case 1:
							if( ((j%128)>42) && ((j%128)<=84) )
							{
								Native.wrMem(0xffffffff, addr_line); // gelb
							}
							else
							{
								Native.wrMem(0xCCCCCCCC, addr_line); // gr�n
							}
						case 2:
							if( ((j%128)>84) && ((j%128)<=128) )
							{
								Native.wrMem(0xffffffff, addr_line); // gelb
							}
							else
							{
								Native.wrMem(0xCCCCCCCC, addr_line); // gr�n
							}
					//	default:
					//		Native.wrMem(0x00000000, addr_line); // schwarz
							
					}
					
				}*/
					
					
	
				Native.wr(s & 1, Const.IO_WD);
			}

			//Native.wr(~s & 1, Const.IO_WD);
			//Native.wr(s & 1, Const.IO_WD);

			next = waitForNextInterval(next);
		}
	}

	static int waitForNextInterval(int next) {

		final int INTERVAL = 1000;		// one ms
		
		if (next==0) {
			next = Native.rd(Const.IO_US_CNT)+INTERVAL;
		} else {
			next += INTERVAL;
		}

		while (next-Native.rd(Const.IO_US_CNT) >= 0)
				;

		return next;
	}

	static void print_02d(int i) {

		int j;
		for (j=0;i>9;++j) i-= 10;
		print_char(j+'0');
		print_char(i+'0');
	}
		
/*
	static void print_04d(int i) {

		if (i<0) {
			print_char('-');
			i = -i;
		}

		int j, k, l;
		for (j=0;i>999;++j) i-= 1000;
		for (k=0;i>99;++k) i-= 100;
		for (l=0;i>9;++l) i-= 10;
		print_char(j+'0');
		print_char(k+'0');
		print_char(l+'0');
		print_char(i+'0');
	}
		

	static void print_06d(int i) {

		if (i<0) {
			print_char('-');
			i = -i;
		}

		int j;

		for (j=0;i>99999;++j) i-= 100000;
		print_char(j+'0');
		for (j=0;i>9999;++j) i-= 10000;
		print_char(j+'0');
		for (j=0;i>999;++j) i-= 1000;
		print_char(j+'0');
		for (j=0;i>99;++j) i-= 100;
		print_char(j+'0');
		for (j=0;i>9;++j) i-= 10;
		print_char(j+'0');
		print_char(i+'0');
	}*/
		


	static void print_hex(int i) {

		int j, k;

		for (j=0; j<8; ++j) {
			k = i>>>((7-j)<<2);
			k &= 0x0f;
			k = k<10 ? k+'0' : k-10+'a';
			print_char(k);
		}
	}


	static void wait_serial() {

		while ((Native.rd(Const.IO_STATUS)&1)==0) ;
	}

	static void print_char(int i) {

		System.out.print((char) i);
		/*
		wait_serial();
		Native.wr(i, Const.IO_UART);
		*/
	}

}
