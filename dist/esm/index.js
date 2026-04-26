import { registerPlugin } from '@capacitor/core';
const AdMobAdvanced = registerPlugin('AdMobAdvanced', {
    web: () => import('./web').then(m => new m.AdMobAdvanced()),
});
export * from './definitions';
export { AdMobAdvanced };
