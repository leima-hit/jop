package gctest;

import util.Dbg;
import com.jopdesign.sys.*;

// A test of the GCStackWalker
// It just tests string collection.

public class GCTest5 {

  public static void main(String s[]) {
	  
    int c = 0;
		for(int i=0;i<1000;i++){
			c++;
			GC.gc(); 
			System.out.println("no crash.no crash.no crash.no crash.no crash.no crash.no crash.no crash.no crash:"+c);
		}
		System.out.println("Test 5 ok");
	} //main
}
