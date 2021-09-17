package com.andrefilgs.lookdown.utils

// import org.junit.Assert.*
import com.andrefilgs.lookdown.assertEqualsTruth
import org.junit.Test

class ExtensionsFileKtTest{

  
  @Test
  fun `test format size 01`(){
    assertEqualsTruth(formatFileSize(1000L), "1,000")
  }
  
  @Test
  fun `test format size 02`(){
    assertEqualsTruth(formatFileSize(1024L), "1KB")
  }
  
  @Test
  fun `test format size 03`(){
    assertEqualsTruth(formatFileSize(1025L), "1KB")
  }
  
  @Test
  fun `test format size 03B`(){
    assertEqualsTruth(formatFileSize(2047L), "1KB")
  }
  
  @Test
  fun `test format size 03C`(){
    assertEqualsTruth(formatFileSize(2048L), "2KB")
  }
  
  @Test
  fun `test format size 03D`(){
    assertEqualsTruth(formatFileSize(2525L), "2KB")
  }
  
  @Test
  fun `test format size 04`(){
    assertEqualsTruth(formatFileSize(1024L * 1024L), "1MB")
  }
  
  @Test
  fun `test format size 05`(){
    assertEqualsTruth(formatFileSize(1024L * 1024L * 1024L), "1,024MB")
  }
}