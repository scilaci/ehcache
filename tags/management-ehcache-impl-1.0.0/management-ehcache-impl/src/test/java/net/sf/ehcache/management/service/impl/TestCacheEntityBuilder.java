/*
 * All content copyright (c) 2003-2012 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package net.sf.ehcache.management.service.impl;

import junit.framework.Assert;
import net.sf.ehcache.management.resource.CacheEntity;
import net.sf.ehcache.management.sampled.ComprehensiveCacheSampler;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author brandony
 */
public class TestCacheEntityBuilder {

  @Test
  public void testMultipleSamplersForSingleCM() {
    String cmName = "CM1";

    ComprehensiveCacheSampler samplerFoo = EasyMock.createMock(ComprehensiveCacheSampler.class);
    EasyMock.expect(samplerFoo.getCacheName()).andReturn("FOO");
    EasyMock.expect(samplerFoo.getExpiredCount()).andReturn(1L);

    ComprehensiveCacheSampler samplerGoo = EasyMock.createMock(ComprehensiveCacheSampler.class);
    EasyMock.expect(samplerGoo.getCacheName()).andReturn("GOO");
    EasyMock.expect(samplerGoo.getExpiredCount()).andReturn(2L);

    ComprehensiveCacheSampler samplerBar = EasyMock.createMock(ComprehensiveCacheSampler.class);
    EasyMock.expect(samplerBar.getCacheName()).andReturn("BAR");
    EasyMock.expect(samplerBar.getExpiredCount()).andReturn(3L);

    EasyMock.replay(samplerFoo, samplerBar, samplerGoo);

    CacheEntityBuilder ceb = CacheEntityBuilder.createWith(samplerFoo, cmName);
    ceb.add(samplerGoo, cmName);
    ceb.add(samplerBar, cmName);

    Set<String> constraintNames = new HashSet<String>(1);
    constraintNames.add("ExpiredCount");

    ceb.add(constraintNames);

    Collection<CacheEntity> ces = ceb.build();

    Assert.assertEquals(3, ces.size());

    Set<String> expectedCaches = new HashSet<String>(3);

    for(CacheEntity ce : ces) {
      expectedCaches.add(ce.getName());
      Assert.assertEquals(1, ce.getAttributes().size());

      Object value = ce.getAttributes().get("ExpiredCount");
      Assert.assertNotNull(value);

      Long expiredCount = Long.class.cast(value);

      if(expiredCount == 1L) {
        Assert.assertEquals("FOO", ce.getName());
      } else if (expiredCount == 2L) {
        Assert.assertEquals("GOO", ce.getName());
      } else if (expiredCount == 3L) {
        Assert.assertEquals("BAR", ce.getName());
      } else {
        Assert.fail("Unexpected attribute value!");
      }
    }

    Assert.assertEquals(3, expectedCaches.size());
  }
}
