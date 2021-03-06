package org.infinispan.xsite;

import static java.lang.String.format;
import static org.testng.AssertJUnit.assertEquals;

import java.util.Arrays;
import java.util.HashSet;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.TakeOfflineConfigurationBuilder;
import org.testng.annotations.Test;

/**
 * @author Mircea Markus
 * @since 5.2
 */
@Test(groups = "xsite", testName = "xsite.XSiteAdminOperationsTest")
public class XSiteAdminOperationsTest extends AbstractTwoSitesTest {

   @Override
   protected ConfigurationBuilder getNycActiveConfig() {
      return getDefaultClusteredCacheConfig(CacheMode.DIST_SYNC, true);
   }

   @Override
   protected ConfigurationBuilder getLonActiveConfig() {
      return getDefaultClusteredCacheConfig(CacheMode.DIST_SYNC, true);
   }

   public void testSiteStatus() {
      assertEquals(admin(LON, 0).siteStatus(NYC), XSiteAdminOperations.ONLINE);
      assertEquals(admin(LON, 1).siteStatus(NYC), XSiteAdminOperations.ONLINE);

      assertEquals(XSiteAdminOperations.SUCCESS, admin(LON, 1).takeSiteOffline(NYC));

      assertEquals(admin(LON, 0).siteStatus(NYC), XSiteAdminOperations.OFFLINE);
      assertEquals(admin(LON, 1).siteStatus(NYC), XSiteAdminOperations.OFFLINE);

      assertEquals(XSiteAdminOperations.SUCCESS, admin(LON, 1).bringSiteOnline(NYC));
      assertEquals(admin(LON, 0).siteStatus(NYC), XSiteAdminOperations.ONLINE);
      assertEquals(admin(LON, 1).siteStatus(NYC), XSiteAdminOperations.ONLINE);
   }

   public void amendTakeOffline() {
      assertEquals(admin(LON, 0).siteStatus(NYC), XSiteAdminOperations.ONLINE);
      assertEquals(admin(LON, 1).siteStatus(NYC), XSiteAdminOperations.ONLINE);

      BackupSenderImpl bs = backupSender(LON, 0);
      OfflineStatus offlineStatus = bs.getOfflineStatus(NYC);
      assertEquals(offlineStatus.getTakeOffline(), new TakeOfflineConfigurationBuilder(null, null).afterFailures(0).minTimeToWait(0).create());

      assertEquals(XSiteAdminOperations.SUCCESS, admin(LON, 1).amendTakeOffline(NYC, 7, 12));
      assertEquals(offlineStatus.getTakeOffline(), new TakeOfflineConfigurationBuilder(null, null).afterFailures(7).minTimeToWait(12).create());

      assertEquals(XSiteAdminOperations.SUCCESS, admin(LON, 1).setTakeOfflineAfterFailures(NYC, 8));
      assertEquals(offlineStatus.getTakeOffline(), new TakeOfflineConfigurationBuilder(null, null).afterFailures(8).minTimeToWait(12).create());

      assertEquals(XSiteAdminOperations.SUCCESS, admin(LON, 1).setTakeOfflineMinTimeToWait(NYC, 13));
      assertEquals(offlineStatus.getTakeOffline(), new TakeOfflineConfigurationBuilder(null, null).afterFailures(8).minTimeToWait(13).create());

      assertEquals(admin(LON, 0).getTakeOfflineAfterFailures(NYC), "8");
      assertEquals(admin(LON, 0).getTakeOfflineMinTimeToWait(NYC), "13");
      assertEquals(admin(LON, 1).getTakeOfflineAfterFailures(NYC), "8");
      assertEquals(admin(LON, 1).getTakeOfflineMinTimeToWait(NYC), "13");
   }

   public void testStatus() {
      assertEquals(admin(LON, 0).status(), format("%s[ONLINE]", NYC));
      assertEquals(admin(LON, 1).status(), format("%s[ONLINE]", NYC));

      assertEquals(XSiteAdminOperations.SUCCESS, admin(LON, 1).takeSiteOffline(NYC));

      assertEquals(admin(LON, 0).status(), format("%s[OFFLINE]", NYC));
      assertEquals(admin(LON, 1).status(), format("%s[OFFLINE]", NYC));

      assertEquals(XSiteAdminOperations.SUCCESS, admin(LON, 1).bringSiteOnline(NYC));
      assertEquals(admin(LON, 0).status(), format("%s[ONLINE]", NYC));
      assertEquals(admin(LON, 1).status(), format("%s[ONLINE]", NYC));

   }

   public void testSitesView() {
      assertEquals(new HashSet<>(Arrays.asList(LON, NYC)),
            site(LON).cacheManagers().get(0).getTransport().getSitesView());
      assertEquals(new HashSet<>(Arrays.asList(LON, NYC)),
            site(NYC).cacheManagers().get(0).getTransport().getSitesView());
   }

   private BackupSenderImpl backupSender(String site, int cache) {
      return (BackupSenderImpl) cache(site, cache).getAdvancedCache().getComponentRegistry().getComponent(BackupSender.class);
   }

   private XSiteAdminOperations admin(String site, int cache) {
      return cache(site, cache).getAdvancedCache().getComponentRegistry().getComponent(XSiteAdminOperations.class);
   }
}
