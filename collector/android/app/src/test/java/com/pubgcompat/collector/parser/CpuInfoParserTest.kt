/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuInfoParserTest {

  private val armSample =
      """
        processor	: 0
        BogoMIPS	: 38.40
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp
        CPU implementer	: 0x41
        CPU architecture: 8
        CPU variant	: 0x0
        CPU part	: 0xd05
        CPU revision	: 3
        """
          .trimIndent()

  private val x86Sample =
      """
        processor	: 0
        vendor_id	: GenuineIntel
        cpu family	: 6
        model		: 142
        model name	: Intel(R) Core(TM) i7-10510U CPU @ 1.80GHz
        stepping	: 12
        cpu cores	: 4
        flags		: fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush dts acpi mmx fxsr sse sse2 ss ht tm pbe syscall nx pdpe1gb rdtscp lm constant_tsc art arch_perfmon pebs bts rep_good nopl xtopology nonstop_tsc cpuid aperfmperf pni pclmulqdq dmesg monitor ds_cpl vmx smx est tm2 ssse3 sdbg fma cx16 xtpr pdcm pcid sse4_1 sse4_2 x2apic movbe popcnt aes xsave osxsave avx f16c rdrand hypervisor lahf_lm abm 3dnowprefetch cpuid_fault epb cat_l3 cdp_l3 invpcid_single ssbd ibrs ibpb stibp ibrs_enhanced fsgsbase tsc_adjust bmi1 avx2 smep bmi2 erms invpcid avx512f avx512dq rdseed adx smap avx512ifma clflushopt clwb intel_pt avx512cd sha_ni avx512bw avx512vl xsaveopt xsavec xgetbv1 xsaves avx512_bf16 dtherm ida arat pln pts hwp hwp_act_window hwp_pkg_req avx512_vnni md_clear flush_l1d arch_capabilities
        bugs		: spectre_v1 spectre_v2 spec_store_bypass
        bogomips	: 4827.54
        """
          .trimIndent()

  private val multiCoreX86 =
      x86Sample +
          "\n\n" +
          "processor\t: 1\nvendor_id\t: GenuineIntel\nmodel name\t: Intel(R) Core(TM) i7-10510U CPU @ 1.80GHz\nflags\t\t: fpu sse avx\n" +
          "\n\n" +
          "processor\t: 2\nvendor_id\t: GenuineIntel\nmodel name\t: Intel(R) Core(TM) i7-10510U CPU @ 1.80GHz\nflags\t\t: fpu sse avx\n"

  @Test
  fun parsesArmLayout() {
    val p = CpuInfoParser.parse(armSample)
    assertEquals("8", p.architecture)
    assertEquals("0x41", p.cpuImplementer)
    assertEquals("0x0", p.cpuVariant)
    assertEquals("0xd05", p.cpuPart)
    assertTrue("aes" in p.features)
    assertTrue("asimddp" in p.features)
    assertEquals(p.features.sorted(), p.features)
    assertEquals(1, p.coreCount)
    assertNull(p.model)
  }

  @Test
  fun parsesX86Layout() {
    val p = CpuInfoParser.parse(x86Sample)
    assertEquals("x86_64", p.architecture)
    assertEquals("Intel(R) Core(TM) i7-10510U CPU @ 1.80GHz", p.model)
    assertEquals("GenuineIntel", p.vendorId)
    assertTrue("hypervisor" in p.features)
    assertEquals(1, p.coreCount)
  }

  @Test
  fun countsCoresFromMultipleBlocks() {
    val p = CpuInfoParser.parse(multiCoreX86)
    assertEquals(3, p.coreCount)
  }

  @Test
  fun toleratesBlankInput() {
    val p = CpuInfoParser.parse("")
    assertNull(p.architecture)
    assertNull(p.coreCount)
    assertTrue(p.features.isEmpty())
  }

  @Test
  fun handlesKvmStyleX86WithNoCpuCountInfo() {
    val kvmSample =
        """
            processor	: 0
            vendor_id	: GenuineIntel
            model name	: Intel(R) Xeon(R) Gold 6238R CPU @ 2.20GHz
            flags		: fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ss ht syscall nx pdpe1gb rdtscp lm constant_tsc rep_good nopl xtopology tsc_reliable hypervisor
            """
            .trimIndent()
    val p = CpuInfoParser.parse(kvmSample)
    assertEquals("x86_64", p.architecture)
    assertTrue("hypervisor" in p.features)
    assertEquals(1, p.coreCount)
  }
}
