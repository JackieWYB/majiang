/**
 * Local Cache Manager for persistent data storage
 * Handles user preferences, game settings, and temporary data caching
 */
export interface CacheConfig {
    maxCacheSize: number; // in bytes
    defaultTTL: number; // in milliseconds
    compressionEnabled: boolean;
    encryptionEnabled: boolean;
}

export interface CacheItem<T = any> {
    key: string;
    value: T;
    timestamp: number;
    ttl: number;
    size: number;
    compressed: boolean;
    encrypted: boolean;
}

export interface CacheStats {
    totalItems: number;
    totalSize: number;
    hitRate: number;
    missRate: number;
    evictions: number;
    lastCleanup: number;
}

export class LocalCacheManager {
    private static _instance: LocalCacheManager;
    private _config: CacheConfig;
    private _cache: Map<string, CacheItem> = new Map();
    private _stats: CacheStats;
    private _cleanupTimer: number = 0;

    public static get instance(): LocalCacheManager {
        if (!this._instance) {
            this._instance = new LocalCacheManager();
        }
        return this._instance;
    }

    private constructor() {
        this._config = {
            maxCacheSize: 5 * 1024 * 1024, // 5MB
            defaultTTL: 24 * 60 * 60 * 1000, // 24 hours
            compressionEnabled: false, // Simplified for this implementation
            encryptionEnabled: false // Simplified for this implementation
        };

        this._stats = {
            totalItems: 0,
            totalSize: 0,
            hitRate: 0,
            missRate: 0,
            evictions: 0,
            lastCleanup: Date.now()
        };

        this.loadCacheFromStorage();
        this.startCleanupTimer();
    }

    /**
     * Set cache item with optional TTL
     */
    public set<T>(key: string, value: T, ttl?: number): void {
        const item: CacheItem<T> = {
            key,
            value,
            timestamp: Date.now(),
            ttl: ttl || this._config.defaultTTL,
            size: this.calculateSize(value),
            compressed: false,
            encrypted: false
        };

        // Check cache size limits
        if (this._stats.totalSize + item.size > this._config.maxCacheSize) {
            this.evictLRU(item.size);
        }

        // Remove existing item if present
        if (this._cache.has(key)) {
            const existing = this._cache.get(key)!;
            this._stats.totalSize -= existing.size;
            this._stats.totalItems--;
        }

        this._cache.set(key, item);
        this._stats.totalItems++;
        this._stats.totalSize += item.size;

        this.persistToStorage(key, item);
    }

    /**
     * Get cache item
     */
    public get<T>(key: string): T | null {
        const item = this._cache.get(key);
        
        if (!item) {
            this._stats.missRate++;
            return null;
        }

        // Check if expired
        if (this.isExpired(item)) {
            this.delete(key);
            this._stats.missRate++;
            return null;
        }

        this._stats.hitRate++;
        return item.value as T;
    }

    /**
     * Check if key exists and is not expired
     */
    public has(key: string): boolean {
        const item = this._cache.get(key);
        return item !== undefined && !this.isExpired(item);
    }

    /**
     * Delete cache item
     */
    public delete(key: string): boolean {
        const item = this._cache.get(key);
        if (!item) {
            return false;
        }

        this._cache.delete(key);
        this._stats.totalItems--;
        this._stats.totalSize -= item.size;
        this.removeFromStorage(key);
        
        return true;
    }

    /**
     * Clear all cache items
     */
    public clear(): void {
        this._cache.clear();
        this._stats.totalItems = 0;
        this._stats.totalSize = 0;
        this._stats.evictions = 0;
        this.clearStorage();
    }

    /**
     * Get cache statistics
     */
    public getStats(): CacheStats {
        const totalRequests = this._stats.hitRate + this._stats.missRate;
        return {
            ...this._stats,
            hitRate: totalRequests > 0 ? this._stats.hitRate / totalRequests : 0,
            missRate: totalRequests > 0 ? this._stats.missRate / totalRequests : 0
        };
    }

    /**
     * Get all cache keys
     */
    public keys(): string[] {
        return Array.from(this._cache.keys());
    }

    /**
     * Get cache size in bytes
     */
    public size(): number {
        return this._stats.totalSize;
    }

    /**
     * Get number of cache items
     */
    public count(): number {
        return this._stats.totalItems;
    }

    /**
     * Cleanup expired items
     */
    public cleanup(): number {
        let removedCount = 0;
        const now = Date.now();

        for (const [key, item] of this._cache.entries()) {
            if (this.isExpired(item)) {
                this.delete(key);
                removedCount++;
            }
        }

        this._stats.lastCleanup = now;
        return removedCount;
    }

    /**
     * Update cache configuration
     */
    public updateConfig(config: Partial<CacheConfig>): void {
        this._config = { ...this._config, ...config };
        
        // If max size reduced, cleanup
        if (this._stats.totalSize > this._config.maxCacheSize) {
            this.evictLRU(this._stats.totalSize - this._config.maxCacheSize);
        }
    }

    /**
     * Get cache configuration
     */
    public getConfig(): CacheConfig {
        return { ...this._config };
    }

    /**
     * Export cache data for backup
     */
    public export(): string {
        const exportData = {
            config: this._config,
            items: Array.from(this._cache.entries()),
            stats: this._stats,
            timestamp: Date.now()
        };
        return JSON.stringify(exportData);
    }

    /**
     * Import cache data from backup
     */
    public import(data: string): boolean {
        try {
            const importData = JSON.parse(data);
            
            if (!importData.items || !Array.isArray(importData.items)) {
                return false;
            }

            this.clear();
            
            for (const [key, item] of importData.items) {
                if (!this.isExpired(item)) {
                    this._cache.set(key, item);
                    this._stats.totalItems++;
                    this._stats.totalSize += item.size;
                }
            }

            if (importData.config) {
                this._config = { ...this._config, ...importData.config };
            }

            return true;
        } catch (error) {
            console.error('Failed to import cache data:', error);
            return false;
        }
    }

    private isExpired(item: CacheItem): boolean {
        return Date.now() - item.timestamp > item.ttl;
    }

    private calculateSize(value: any): number {
        // Rough estimation of object size in bytes
        return JSON.stringify(value).length * 2;
    }

    private evictLRU(requiredSpace: number): void {
        const items = Array.from(this._cache.entries())
            .sort(([, a], [, b]) => a.timestamp - b.timestamp);

        let freedSpace = 0;
        for (const [key, item] of items) {
            if (freedSpace >= requiredSpace) {
                break;
            }
            
            this.delete(key);
            freedSpace += item.size;
            this._stats.evictions++;
        }
    }

    private startCleanupTimer(): void {
        this._cleanupTimer = setInterval(() => {
            this.cleanup();
        }, 60000) as any; // Cleanup every minute
    }

    private stopCleanupTimer(): void {
        if (this._cleanupTimer) {
            clearInterval(this._cleanupTimer);
            this._cleanupTimer = 0;
        }
    }

    private loadCacheFromStorage(): void {
        try {
            const keys = this.getStorageKeys();
            
            for (const key of keys) {
                if (key.startsWith('cache_')) {
                    const cacheKey = key.substring(6); // Remove 'cache_' prefix
                    const stored = this.getStoredData(key);
                    
                    if (stored) {
                        const item: CacheItem = JSON.parse(stored);
                        if (!this.isExpired(item)) {
                            this._cache.set(cacheKey, item);
                            this._stats.totalItems++;
                            this._stats.totalSize += item.size;
                        } else {
                            this.removeFromStorage(cacheKey);
                        }
                    }
                }
            }
        } catch (error) {
            console.warn('Failed to load cache from storage:', error);
        }
    }

    private persistToStorage(key: string, item: CacheItem): void {
        try {
            this.setStoredData(`cache_${key}`, JSON.stringify(item));
        } catch (error) {
            console.error('Failed to persist cache item:', error);
        }
    }

    private removeFromStorage(key: string): void {
        try {
            this.removeStoredData(`cache_${key}`);
        } catch (error) {
            console.error('Failed to remove cache item from storage:', error);
        }
    }

    private clearStorage(): void {
        try {
            const keys = this.getStorageKeys();
            for (const key of keys) {
                if (key.startsWith('cache_')) {
                    this.removeStoredData(key);
                }
            }
        } catch (error) {
            console.error('Failed to clear cache storage:', error);
        }
    }

    private getStorageKeys(): string[] {
        if (typeof wx !== 'undefined') {
            // WeChat Mini Program - get all storage keys
            try {
                const info = wx.getStorageInfoSync();
                return info.keys || [];
            } catch (error) {
                return [];
            }
        } else if (typeof localStorage !== 'undefined') {
            // Web/simulator
            return Object.keys(localStorage);
        }
        return [];
    }

    private getStoredData(key: string): string | null {
        if (typeof wx !== 'undefined') {
            try {
                return wx.getStorageSync(key) || null;
            } catch (error) {
                return null;
            }
        } else if (typeof localStorage !== 'undefined') {
            return localStorage.getItem(key);
        }
        return null;
    }

    private setStoredData(key: string, value: string): void {
        if (typeof wx !== 'undefined') {
            wx.setStorageSync(key, value);
        } else if (typeof localStorage !== 'undefined') {
            localStorage.setItem(key, value);
        }
    }

    private removeStoredData(key: string): void {
        if (typeof wx !== 'undefined') {
            wx.removeStorageSync(key);
        } else if (typeof localStorage !== 'undefined') {
            localStorage.removeItem(key);
        }
    }

    /**
     * Cleanup resources
     */
    public destroy(): void {
        this.stopCleanupTimer();
        this.clear();
    }
}