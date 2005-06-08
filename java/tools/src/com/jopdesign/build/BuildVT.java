/*
 * Created on 04.06.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.jopdesign.build;

import java.util.*;
import org.apache.bcel.classfile.*;

/**
 * 
 * Build virtual method tables and the one and only interface table.
 * 
 * @author martin
 *
 */
public class BuildVT extends MyVisitor {

	Map mapClVT = new HashMap();

	
	public BuildVT(JOPizer jz) {
		super(jz);
	}
	
	public void visitJavaClass(JavaClass clazz) {

		super.visitJavaClass(clazz);
		// don't get confused in the build process
		cli = null;
		// TODO: if (!array class) ...
		buildVT(clazz);
		if (clazz.isInterface()) {
			buildIT(clazz);
		}
	}

	
	/**
	 * Called recursive to build the VTs top down.
	 * 
	 * @param clazz
	 */
	private void buildVT(JavaClass clazz) {

		int i, j;

// System.err.println("invoke buildVT on class: "+clazz);
		ClassInfo cli;
		cli = (ClassInfo) ClassInfo.mapClassNames.get(clazz.getClassName());
// System.err.println("build VT on class: "+cli.clazz);

/*
 * TODO: we have TWO mappings of clazzes: in JOPWriter AND in ClassInfo
 * However, they are not consistent!
 * The following results in a null pointer for java.lang.Object:
		cli = jz.getClassInfo(clazz);
System.err.println("build VT on class: "+cli.clazz);
*/

		ClassInfo.ClVT supVt = null;
		if (clazz.getClassName().equals(clazz.getSuperclassName())) {
			;	// now we'r Object
		} else {
			ClassInfo clisup = ClassInfo.getClassInfo(clazz.getSuperclassName());

//					JavaClass superClazz = clazz.getSuperClass();
//			 System.err.println("super: "+superClazz);
			JavaClass superClazz = clisup.clazz;
			String superName = superClazz.getClassName();
// System.err.println("super name: "+superName);
			if (mapClVT.get(superName)==null) {	
// System.err.println("rec. invoke buildVT with: "+superClazz);
				buildVT(superClazz);	// first build super VT
			}
			supVt = (ClassInfo.ClVT) mapClVT.get(superName);
		}
// System.err.println("build VT on: "+clazz.getClassName());
		String clazzName = clazz.getClassName();
		if (mapClVT.get(clazzName)!=null) {	
			return;									// allready done!
		}

// this also tries to load from application CLASSPATH
//		int intfCount =  clazz.getInterfaces().length;

// System.err.println(cli);
		ClassInfo.ClVT clvt = cli.getClVT();
		mapClVT.put(clazzName, clvt);

		Method m[] = clazz.getMethods();
		int methodCount =  m.length;

		int maxLen = methodCount;
		if (supVt!=null) maxLen += supVt.len;
		clvt.len = 0;
		clvt.key = new String[maxLen];
		clvt.ptr = new int[maxLen];
		clvt.mi = new MethodInfo[maxLen];

// System.out.println("// VT: "+clazzName);

		if (supVt!=null) {
			for (i=0; i<supVt.len; ++i) {
				clvt.key[i] = supVt.key[i];
// System.out.println("//super: "+clvt.key[i]);
				clvt.mi[i] = supVt.mi[i];
			}
			clvt.len = supVt.len;
		}

		for (i = 0; i < methodCount; i++) { 
			Method meth = m[i];
			String methodId = meth.getName()+meth.getSignature();
			MethodInfo mi = cli.getMethodInfo(methodId);
			
			for (j=0; j<clvt.len; ++j) {
				if (clvt.key[j].equals(methodId)) {					// override method
//System.out.println("override "+methodId);
					clvt.mi[j] = mi;
					break;
				}
			}
			if (j==clvt.len) {								// new method
				clvt.key[clvt.len] = methodId;
//System.out.println("new "+methodId);
				clvt.mi[clvt.len] = mi;
				++clvt.len;
			}

		}
//System.out.println("The VT of "+clazzName);
//for (i=0; i<clvt.len; i++) { 
//	System.out.println("//\t"+clvt.meth[i].cli.clazz.getClassName()+"."+clvt.key[i]);
//}

	}

	private void buildIT(JavaClass clazz) {

		int i, j;

		ClassInfo cli = ClassInfo.getClassInfo(clazz.getClassName());

		String clazzName = clazz.getClassName();

		Method m[] = clazz.getMethods();
		int methodCount =  m.length;


		//
		//	build global interface table
		//

		for (i = 0; i < methodCount; i++) { 
			Method meth = m[i];
			String methodId = meth.getName()+meth.getSignature();
			MethodInfo mi = cli.getMethodInfo(methodId);

			ClassInfo.IT it = ClassInfo.getITObject();
			it.nr = ClassInfo.listIT.size();
			it.key = methodId;
// System.out.println("Add to IT: "+it.nr+" "+it.key);
			it.meth = mi;
			ClassInfo.listIT.add(it);

		}
		
	}

}
