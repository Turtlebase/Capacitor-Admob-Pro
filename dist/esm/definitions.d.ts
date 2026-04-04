export interface AdMobAdvancedPlugin {
    initialize(): Promise<void>;
    showBanner(options: {
        adId: string;
    }): Promise<void>;
    hideBanner(): Promise<void>;
}
