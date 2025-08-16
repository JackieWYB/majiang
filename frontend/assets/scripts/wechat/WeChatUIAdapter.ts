import { _decorator, Component, Canvas, view, ResolutionPolicy, screen } from 'cc';
import { WeChatAPI } from './WeChatAPI';

const { ccclass, property } = _decorator;

export interface DeviceInfo {
    brand: string;
    model: string;
    pixelRatio: number;
    screenWidth: number;
    screenHeight: number;
    windowWidth: number;
    windowHeight: number;
    statusBarHeight: number;
    safeArea: {
        top: number;
        left: number;
        right: number;
        bottom: number;
        width: number;
        height: number;
    };
    isIPhoneX: boolean;
    isIOS: boolean;
    isAndroid: boolean;
}

/**
 * WeChat UI adapter for responsive design and device compatibility
 */
@ccclass('WeChatUIAdapter')
export class WeChatUIAdapter extends Component {
    private static instance: WeChatUIAdapter;
    private wechatAPI: WeChatAPI;
    private deviceInfo: DeviceInfo | null = null;
    private canvas: Canvas | null = null;

    @property({ type: Canvas })
    public targetCanvas: Canvas | null = null;

    private constructor() {
        super();
        this.wechatAPI = WeChatAPI.getInstance();
    }

    public static getInstance(): WeChatUIAdapter {
        if (!WeChatUIAdapter.instance) {
            WeChatUIAdapter.instance = new WeChatUIAdapter();
        }
        return WeChatUIAdapter.instance;
    }

    protected onLoad(): void {
        this.canvas = this.targetCanvas || this.getComponent(Canvas);
        this.initializeAdapter();
    }

    /**
     * Initialize UI adapter
     */
    private async initializeAdapter(): Promise<void> {
        try {
            // Get device information
            this.deviceInfo = await this.getDeviceInfo();
            
            // Setup canvas resolution
            this.setupCanvasResolution();
            
            // Setup safe area
            this.setupSafeArea();
            
            // Setup responsive design
            this.setupResponsiveDesign();
            
            console.log('UI Adapter initialized:', this.deviceInfo);
        } catch (error) {
            console.error('Failed to initialize UI adapter:', error);
        }
    }

    /**
     * Get device information
     */
    private async getDeviceInfo(): Promise<DeviceInfo> {
        const systemInfo = await this.wechatAPI.getSystemInfo();
        
        const deviceInfo: DeviceInfo = {
            brand: systemInfo.brand || 'unknown',
            model: systemInfo.model || 'unknown',
            pixelRatio: systemInfo.pixelRatio || 2,
            screenWidth: systemInfo.screenWidth || 375,
            screenHeight: systemInfo.screenHeight || 667,
            windowWidth: systemInfo.windowWidth || 375,
            windowHeight: systemInfo.windowHeight || 667,
            statusBarHeight: systemInfo.statusBarHeight || 20,
            safeArea: systemInfo.safeArea || {
                top: systemInfo.statusBarHeight || 20,
                left: 0,
                right: systemInfo.windowWidth || 375,
                bottom: systemInfo.windowHeight || 667,
                width: systemInfo.windowWidth || 375,
                height: (systemInfo.windowHeight || 667) - (systemInfo.statusBarHeight || 20)
            },
            isIPhoneX: this.isIPhoneX(systemInfo),
            isIOS: systemInfo.platform === 'ios',
            isAndroid: systemInfo.platform === 'android'
        };

        return deviceInfo;
    }

    /**
     * Check if device is iPhone X series
     */
    private isIPhoneX(systemInfo: any): boolean {
        const { screenWidth, screenHeight, model } = systemInfo;
        
        // iPhone X series detection
        const iPhoneXModels = [
            'iPhone X', 'iPhone XS', 'iPhone XS Max', 'iPhone XR',
            'iPhone 11', 'iPhone 11 Pro', 'iPhone 11 Pro Max',
            'iPhone 12', 'iPhone 12 mini', 'iPhone 12 Pro', 'iPhone 12 Pro Max',
            'iPhone 13', 'iPhone 13 mini', 'iPhone 13 Pro', 'iPhone 13 Pro Max',
            'iPhone 14', 'iPhone 14 Plus', 'iPhone 14 Pro', 'iPhone 14 Pro Max'
        ];

        if (model && iPhoneXModels.some(m => model.includes(m))) {
            return true;
        }

        // Fallback: check screen dimensions
        const aspectRatio = Math.max(screenWidth, screenHeight) / Math.min(screenWidth, screenHeight);
        return aspectRatio > 2.0; // iPhone X series have aspect ratio > 2.0
    }

    /**
     * Setup canvas resolution
     */
    private setupCanvasResolution(): void {
        if (!this.canvas || !this.deviceInfo) {
            return;
        }

        const { windowWidth, windowHeight, pixelRatio } = this.deviceInfo;
        
        // Set design resolution
        const designWidth = 750; // Design width in pixels
        const designHeight = 1334; // Design height in pixels
        
        view.setDesignResolutionSize(
            designWidth,
            designHeight,
            ResolutionPolicy.SHOW_ALL
        );

        // Adjust for pixel ratio
        const actualWidth = windowWidth * pixelRatio;
        const actualHeight = windowHeight * pixelRatio;
        
        console.log(`Canvas resolution: ${actualWidth}x${actualHeight}, Design: ${designWidth}x${designHeight}`);
    }

    /**
     * Setup safe area handling
     */
    private setupSafeArea(): void {
        if (!this.deviceInfo) {
            return;
        }

        const { safeArea, isIPhoneX } = this.deviceInfo;
        
        // Apply safe area margins to UI elements
        this.applySafeAreaMargins(safeArea);
        
        // Special handling for iPhone X series
        if (isIPhoneX) {
            this.applyIPhoneXOptimizations();
        }
    }

    /**
     * Apply safe area margins
     */
    private applySafeAreaMargins(safeArea: any): void {
        // This would typically adjust UI elements to avoid safe area
        // For example, move buttons away from screen edges
        
        const topMargin = safeArea.top;
        const bottomMargin = this.deviceInfo!.windowHeight - safeArea.bottom;
        
        console.log(`Safe area margins - Top: ${topMargin}, Bottom: ${bottomMargin}`);
        
        // Apply margins to specific UI elements
        this.adjustUIForSafeArea(topMargin, bottomMargin);
    }

    /**
     * Adjust UI elements for safe area
     */
    private adjustUIForSafeArea(topMargin: number, bottomMargin: number): void {
        // Find and adjust UI elements that need safe area handling
        // This would be implemented based on specific UI structure
        
        // Example: Adjust header and footer positions
        const headerElements = this.node.getComponentsInChildren('HeaderComponent');
        const footerElements = this.node.getComponentsInChildren('FooterComponent');
        
        // Adjust positions (pseudo-code)
        // headerElements.forEach(header => header.adjustForSafeArea(topMargin));
        // footerElements.forEach(footer => footer.adjustForSafeArea(bottomMargin));
    }

    /**
     * Apply iPhone X specific optimizations
     */
    private applyIPhoneXOptimizations(): void {
        console.log('Applying iPhone X optimizations');
        
        // Add specific optimizations for iPhone X series
        // - Adjust for notch
        // - Handle home indicator area
        // - Optimize gesture areas
    }

    /**
     * Setup responsive design
     */
    private setupResponsiveDesign(): void {
        if (!this.deviceInfo) {
            return;
        }

        const { windowWidth, windowHeight } = this.deviceInfo;
        const aspectRatio = windowWidth / windowHeight;
        
        // Determine device category
        let deviceCategory = 'normal';
        if (aspectRatio > 0.7) {
            deviceCategory = 'wide'; // Tablet-like
        } else if (aspectRatio < 0.5) {
            deviceCategory = 'narrow'; // Very tall phones
        }
        
        console.log(`Device category: ${deviceCategory}, Aspect ratio: ${aspectRatio}`);
        
        // Apply responsive adjustments
        this.applyResponsiveAdjustments(deviceCategory);
    }

    /**
     * Apply responsive adjustments
     */
    private applyResponsiveAdjustments(deviceCategory: string): void {
        switch (deviceCategory) {
            case 'wide':
                this.applyWideScreenAdjustments();
                break;
            case 'narrow':
                this.applyNarrowScreenAdjustments();
                break;
            default:
                this.applyNormalScreenAdjustments();
                break;
        }
    }

    /**
     * Apply wide screen adjustments
     */
    private applyWideScreenAdjustments(): void {
        console.log('Applying wide screen adjustments');
        // Adjust layout for wider screens (tablets)
        // - Use more horizontal space
        // - Adjust button sizes and positions
        // - Optimize for landscape orientation
    }

    /**
     * Apply narrow screen adjustments
     */
    private applyNarrowScreenAdjustments(): void {
        console.log('Applying narrow screen adjustments');
        // Adjust layout for very tall screens
        // - Optimize vertical space usage
        // - Adjust font sizes
        // - Modify button layouts
    }

    /**
     * Apply normal screen adjustments
     */
    private applyNormalScreenAdjustments(): void {
        console.log('Applying normal screen adjustments');
        // Standard adjustments for typical phone screens
    }

    /**
     * Get device information
     */
    public getDeviceInfo(): DeviceInfo | null {
        return this.deviceInfo;
    }

    /**
     * Check if device is iPhone X series
     */
    public isIPhoneXSeries(): boolean {
        return this.deviceInfo?.isIPhoneX || false;
    }

    /**
     * Get safe area insets
     */
    public getSafeAreaInsets(): { top: number; bottom: number; left: number; right: number } {
        if (!this.deviceInfo) {
            return { top: 0, bottom: 0, left: 0, right: 0 };
        }

        const { safeArea, windowWidth, windowHeight } = this.deviceInfo;
        
        return {
            top: safeArea.top,
            bottom: windowHeight - safeArea.bottom,
            left: safeArea.left,
            right: windowWidth - safeArea.right
        };
    }

    /**
     * Convert design pixels to actual pixels
     */
    public dp2px(dp: number): number {
        if (!this.deviceInfo) {
            return dp;
        }
        
        const scale = this.deviceInfo.windowWidth / 750; // Assuming 750px design width
        return dp * scale;
    }

    /**
     * Convert actual pixels to design pixels
     */
    public px2dp(px: number): number {
        if (!this.deviceInfo) {
            return px;
        }
        
        const scale = this.deviceInfo.windowWidth / 750; // Assuming 750px design width
        return px / scale;
    }

    /**
     * Get optimal font size for device
     */
    public getOptimalFontSize(baseFontSize: number): number {
        if (!this.deviceInfo) {
            return baseFontSize;
        }

        const { windowWidth } = this.deviceInfo;
        const scale = windowWidth / 375; // Base on iPhone 6/7/8 width
        
        return Math.max(12, baseFontSize * scale); // Minimum 12px
    }

    /**
     * Handle orientation change
     */
    public handleOrientationChange(): void {
        // Re-initialize adapter on orientation change
        setTimeout(() => {
            this.initializeAdapter();
        }, 100);
    }

    /**
     * Update layout for current device
     */
    public updateLayout(): void {
        if (this.deviceInfo) {
            this.setupResponsiveDesign();
        }
    }
}