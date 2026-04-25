import { registerPlugin } from '@capacitor/core';
import type { AdMobAdvancedPlugin } from './definitions';

const AdMobAdvanced = registerPlugin<AdMobAdvancedPlugin>('AdMobAdvanced', {
  web: () => import('./web').then(m => new m.AdMobAdvanced()),
});

export * from './definitions';
export { AdMobAdvanced };
