/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.sun.javatest.regtest.config.JDKOpts;

/**
 * Unit test for com.sun.javatest.regtest.config.JDKOpts,
 * which is used to create command lines for java and javac,
 * combining/merging options which should otherwise only appear
 * once, taking alternative forms into account.
 *
 * When run with no args, it executes the built-in test cases.
 * When run with args, it just runs a single test case based on those args.
 */
public class JDKOptsTest {
    public static void main(String... args) throws Exception {
        new JDKOptsTest().run(args);
    }

    @Test
    void testClassPath() {
        String[] opts = { "-classpath", "a", "-classpath", "b" };
        String[] expect = { "-classpath", "a" + PS + "b" };
        test(opts, expect);
    }

    @Test
    void testSourcePath() {
        String[] opts = { "-sourcepath", "a", "-sourcepath", "b" };
        String[] expect = { "-sourcepath", "a" + PS + "b" };
        test(opts, expect);
    }

    @Test
    void testAddMods() {
        String[] opts = { "-addmods", "m1,m2", "-addmods", "m2,m3" };
        String[] expect = { "-addmods", "m1,m2,m3" };
        test(opts, expect);
    }

    @Test
    void testLimitMods() {
        String[] opts = { "-limitmods", "m2,m1", "-limitmods", "m3,m2" };
        String[] expect = { "-limitmods", "m2,m1,m3" };
        test(opts, expect);
    }

    @Test
    void testXpatch_sameModule_differentPatches() {
        String[] opts = { "-Xpatch:a=" + file(patchDir1, "a"), "-Xpatch:a=" + file(patchDir2, "a") };
        String[] expect = { "-Xpatch:a=" + file(patchDir1, "a") + PS + file(patchDir2, "a") };
        test(opts, expect);
    }

    @Test
    void testXpatch_differentModules() {
        String[] opts = { "-Xpatch:a=" + file(patchDir1, "a"), "-Xpatch:b=" + file(patchDir2, "b") };
        String[] expect = { "-Xpatch:a=" + file(patchDir1, "a"), "-Xpatch:b=" + file(patchDir2, "b") };
        test(opts, expect);
    }

    @Test
    void testXaddExports() {
        String[] opts = { "-XaddExports:m1/p1=ALL-UNNAMED", "-XaddExports:m2/p2=ALL-UNNAMED", "-XaddExports:m1/p1=m11" };
        String[] expect = { "-XaddExports:m1/p1=ALL-UNNAMED,m11", "-XaddExports:m2/p2=ALL-UNNAMED" };
        test(opts, expect);
    }

    @Test
    void testMix() {
        String[] opts = {
            "-classpath", "cp1", "-sourcepath", "sp1", "-Xpatch:xp1=xp1", "-XaddExports:m1/p1=ALL-UNNAMED",
            "-classpath", "cp2", "-sourcepath", "sp2", "-Xpatch:xp2=xp2", "-XaddExports:m2/p2=ALL-UNNAMED",
            "-classpath", "cp3", "-sourcepath", "sp3", "-Xpatch:xp3=xp3", "-XaddExports:m3/p3=ALL-UNNAMED",
            "-addmods", "m1,m2,m3",
            "-limitmods", "m1,m2,m3",
            "-Xpatch:xp1=xp1a",
            "-Xpatch:xp2=xp2a",
            "-Xpatch:xp3=xp3a",
            "-XaddExports:m1/p1=m11",
            "-XaddExports:m2/p2=m22",
            "-XaddExports:m3/p3=m33",
            "-addmods", "m2,m3,m4",
            "-limitmods", "m2,m3,m4",
        };
        String[] expect = {
            "-classpath", "cp1" + PS + "cp2" + PS + "cp3",
            "-sourcepath", "sp1" + PS + "sp2" + PS + "sp3",
            "-Xpatch:xp1=xp1" + PS + "xp1a",
            "-XaddExports:m1/p1=ALL-UNNAMED,m11",
            "-Xpatch:xp2=xp2" + PS + "xp2a",
            "-XaddExports:m2/p2=ALL-UNNAMED,m22",
            "-Xpatch:xp3=xp3" + PS + "xp3a",
            "-XaddExports:m3/p3=ALL-UNNAMED,m33",
            "-addmods", "m1,m2,m3,m4",
            "-limitmods", "m1,m2,m3,m4"
        };
        test(opts, expect);
    }

    @Test
    void testClassPathVariants() {
        String[] opts = { "-cp", "a", "-classpath", "b", "--class-path", "c", "--class-path=d" };
        String[] expectOld = { "-classpath", "a" + PS + "b" + PS + "c" + PS + "d" };
        String[] expectNew = { "--class-path", "a" + PS + "b" + PS + "c" + PS + "d" };
        test(opts, expectOld, expectNew);
    }

    @Test
    void testSourcePathVariants() {
        String[] opts = { "-sourcepath", "a", "--source-path", "b", "--source-path=c" };
        String[] expectOld = { "-sourcepath", "a" + PS + "b" + PS + "c" };
        String[] expectNew = { "--source-path", "a" + PS + "b" + PS + "c" };
        test(opts, expectOld, expectNew);
    }

    @Test
    void testAddModulesVariants() {
        String[] opts = { "-addmods", "a", "--add-modules", "b", "--add-modules=c" };
        String[] expectOld = { "-addmods", "a,b,c" };
        String[] expectNew = { "--add-modules", "a,b,c" };
        test(opts, expectOld, expectNew);
    }

    @Test
    void testLimitModulesVariants() {
        String[] opts = { "-limitmods", "a", "--limit-modules", "b", "--limit-modules=c" };
        String[] expectOld = { "-limitmods", "a,b,c" };
        String[] expectNew = { "--limit-modules", "a,b,c" };
        test(opts, expectOld, expectNew);
    }

    @Test
    void testAddExportsVariants() {
        String[] opts = {
                "-XaddExports:m1/p1=a", "--add-exports", "m1/p1=b", "--add-exports=m1/p1=c",
                "-XaddExports:m2/p2=d", "--add-exports", "m2/p2=e", "--add-exports=m2/p2=f"};
        String[] expectOld = { "-XaddExports:m1/p1=a,b,c", "-XaddExports:m2/p2=d,e,f" };
        String[] expectNew = { "--add-exports", "m1/p1=a,b,c", "--add-exports", "m2/p2=d,e,f" };
        test(opts, expectOld, expectNew);
    }

    @Test
    void testAddReadsVariants() {
        String[] opts = {
                "-XaddReads:m1=a", "--add-reads", "m1=b", "--add-reads=m1=c",
                "-XaddReads:m2=d", "--add-reads", "m2=e", "--add-reads=m2=f"};
        String[] expectOld = { "-XaddReads:m1=a,b,c", "-XaddReads:m2=d,e,f" };
        String[] expectNew = { "--add-reads", "m1=a,b,c", "--add-reads", "m2=d,e,f" };
        test(opts, expectOld, expectNew);
    }

    @Test
    void testPatchModulesVariants() {
        String[] opts = {
                "-Xpatch:m1=a", "--patch-module", "m1=b", "--patch-module=m1=c",
                "-Xpatch:m2=d", "--patch-module", "m2=e", "--patch-module=m2=f"};
        String[] expectOld = { "-Xpatch:m1=a" + PS + "b" + PS + "c", "-Xpatch:m2=d" + PS + "e" + PS + "f" };
        String[] expectNew = { "--patch-module", "m1=a" + PS + "b" + PS + "c", "--patch-module", "m2=d" + PS + "e" + PS + "f" };
        test(opts, expectOld, expectNew);
    }

    static final String PS = File.pathSeparator;

    /** Marker annotation for test cases. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Test { }

    final File patchDir1;
    final File patchDir2;

    JDKOptsTest() {
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        System.err.println("baseDir: " + baseDir);
        patchDir1 = new File(baseDir, "patch1");
        createDummyPatches(patchDir1, "a", "b", "c");

        patchDir2 = new File(baseDir, "patch2");
        createDummyPatches(patchDir2, "a", "b", "c");
    }

    void createDummyPatches(File patchDir, String... modules) {
        for (String m: modules) {
            new File(patchDir, m).mkdirs();
        }
    }

    File file(File dir, String name) {
        return new File(dir, name);
    }

    void run(String... args) throws Exception {
        if (args.length == 0) {
            runTests();
        } else {
            JDKOpts oldOpts = new JDKOpts(false);
            oldOpts.addAll(args);
            System.out.println("Old: " + oldOpts.toList());
            JDKOpts newOpts = new JDKOpts(true);
            newOpts.addAll(args);
            System.out.println("New: " + newOpts.toList());
        }
    }

    /**
     * Combo test to run all test cases in all modes.
     */
    void runTests() throws Exception {
        for (Method m : getClass().getDeclaredMethods()) {
            Annotation a = m.getAnnotation(Test.class);
            if (a != null) {
                try {
                    System.out.println("Test: " + m.getName());
                    m.invoke(this);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    throw (cause instanceof Exception) ? ((Exception) cause) : e;
                }
                System.err.println();
            }
        }
        System.err.println(testCount + " tests" + ((errorCount == 0) ? "" : ", " + errorCount + " errors"));
        if (errorCount > 0) {
            throw new Exception(errorCount + " errors found");
        }
    }

    void test(String[] opts, String[] expect) {
        testCount++;
        JDKOpts jdkOpts = new JDKOpts(false);
        jdkOpts.addAll(opts);
        List<String> result = jdkOpts.toList();
        System.out.println("Options: " + Arrays.toString(opts));
        System.out.println("Expect:  " + Arrays.toString(expect));
        System.out.println("Found:   " + result);
        if (!result.equals(Arrays.asList(expect))) {
            System.out.println("ERROR");
            errorCount++;
        }
    }

    void test(String[] opts, String[] expectOld, String[] expectNew) {
        testCount++;

        JDKOpts oldOpts = new JDKOpts(false);
        oldOpts.addAll(opts);
        List<String> resultOld = oldOpts.toList();

        JDKOpts newOpts = new JDKOpts(true);
        newOpts.addAll(opts);
        List<String> resultNew = newOpts.toList();

        System.out.println("Options:    " + Arrays.toString(opts));
        System.out.println("Expect old: " + Arrays.toString(expectOld));
        System.out.println("Found old : " + resultOld);
        System.out.println("Expect new: " + Arrays.toString(expectNew));
        System.out.println("Found new : " + resultNew);
        if (!resultOld.equals(Arrays.asList(expectOld))
                || !resultNew.equals(Arrays.asList(expectNew))) {
            System.out.println("ERROR");
            errorCount++;
        }
    }

    int testCount;
    int errorCount;
}

