import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { LocalCacheManager, CacheConfig, CacheStats } from '../../assets/scripts/managers/LocalCacheManager';

// Mock localStorage
const mockLocalStorage = {
    store: new Map<string, string>(),
    getItem: vi.fn((key: string) => mockLocalStorage.store.get(key) || null),
    setItem: vi.fn((key: string, value: string) => {
        mockLocalStorage.store.set(key, value);
        return undefined;
    }),
    removeItem: vi.fn((key: string) => {
        mockLocalStorage.store.delete(key);
        return undefined;
    }),
    clear: vi.fn(() => mockLocalStorage.store.clear()),
    get length() { return mockLocalStorage.store.size; },
    key: vi.fn((index: number) => {
        const keys = Array.from(mockLocalStorage.store.keys());
        return keys[index] || null;
    })
};

Object.defineProperty(global, 'localStorage', {
    value: mockLocalStorage,
    writable: true
});

// Mock Object.keys for localStorage
Object.keys = vi.fn((obj) => {
    if (obj === mockLocalStorage) {
        return Array.from(mockLocalStorage.store.keys());
    }
    return Object.getOwnPropertyNames(obj);
});

describe('LocalCacheManager', () => {
    let cacheManager: LocalCacheManager;

    beforeEach(() => {
        // Reset singleton instance
        (LocalCacheManager as any)._instance = null;
        cacheManager = LocalCacheManager.instance;
        
        // Clear localStorage mock
        mockLocalStorage.store.clear();
        vi.clearAllMocks();
    });

    afterEach(() => {
        cacheManager.destroy();
    });

    describe('Basic Operations', () => {
        it('should create singleton instance', () => {
            const instance1 = LocalCacheManager.instance;
            const instance2 = LocalCacheManager.instance;
            expect(instance1).toBe(instance2);
        });

        it('should set and get cache items', () => {
            const testData = { name: 'test', value: 123 };
            
            cacheManager.set('test-key', testData);
            const retrieved = cacheManager.get('test-key');
            
            expect(retrieved).toEqual(testData);
        });

        it('should return null for non-existent keys', () => {
            const result = cacheManager.get('non-existent');
            expect(result).toBeNull();
        });

        it('should check if key exists', () => {
            cacheManager.set('existing-key', 'value');
            
            expect(cacheManager.has('existing-key')).toBe(true);
            expect(cacheManager.has('non-existent')).toBe(false);
        });

        it('should delete cache items', () => {
            cacheManager.set('delete-me', 'value');
            expect(cacheManager.has('delete-me')).toBe(true);
            
            const deleted = cacheManager.delete('delete-me');
            expect(deleted).toBe(true);
            expect(cacheManager.has('delete-me')).toBe(false);
        });

        it('should return false when deleting non-existent key', () => {
            const deleted = cacheManager.delete('non-existent');
            expect(deleted).toBe(false);
        });

        it('should clear all cache items', () => {
            cacheManager.set('key1', 'value1');
            cacheManager.set('key2', 'value2');
            
            expect(cacheManager.count()).toBe(2);
            
            cacheManager.clear();
            expect(cacheManager.count()).toBe(0);
        });
    });

    describe('TTL and Expiration', () => {
        it('should respect TTL for cache items', async () => {
            const shortTTL = 100; // 100ms
            cacheManager.set('short-lived', 'value', shortTTL);
            
            expect(cacheManager.get('short-lived')).toBe('value');
            
            // Wait for expiration
            await new Promise(resolve => setTimeout(resolve, 150));
            
            expect(cacheManager.get('short-lived')).toBeNull();
        });

        it('should use default TTL when not specified', () => {
            cacheManager.set('default-ttl', 'value');
            expect(cacheManager.has('default-ttl')).toBe(true);
        });

        it('should cleanup expired items', async () => {
            const shortTTL = 50;
            cacheManager.set('expire1', 'value1', shortTTL);
            cacheManager.set('expire2', 'value2', shortTTL);
            cacheManager.set('keep', 'value3', 10000); // Long TTL
            
            expect(cacheManager.count()).toBe(3);
            
            // Wait for expiration
            await new Promise(resolve => setTimeout(resolve, 100));
            
            const removedCount = cacheManager.cleanup();
            expect(removedCount).toBe(2);
            expect(cacheManager.count()).toBe(1);
            expect(cacheManager.has('keep')).toBe(true);
        });
    });

    describe('Size Management', () => {
        it('should track cache size', () => {
            const initialSize = cacheManager.size();
            
            cacheManager.set('size-test', { data: 'test data' });
            
            expect(cacheManager.size()).toBeGreaterThan(initialSize);
        });

        it('should track item count', () => {
            expect(cacheManager.count()).toBe(0);
            
            cacheManager.set('item1', 'value1');
            cacheManager.set('item2', 'value2');
            
            expect(cacheManager.count()).toBe(2);
        });

        it('should evict LRU items when cache is full', () => {
            // Set small cache size
            cacheManager.updateConfig({ maxCacheSize: 1000 });
            
            // Add items that exceed cache size
            const largeData = 'x'.repeat(300); // Smaller data to ensure proper eviction
            cacheManager.set('item1', largeData);
            cacheManager.set('item2', largeData);
            cacheManager.set('item3', largeData); // This should trigger eviction
            
            // Should have evicted at least one item due to size constraints
            const totalItems = cacheManager.count();
            expect(totalItems).toBeLessThan(3);
            
            // The most recent item should still exist
            expect(cacheManager.has('item3')).toBe(true);
        });
    });

    describe('Statistics', () => {
        it('should track cache statistics', () => {
            cacheManager.set('stats-test', 'value');
            
            // Hit
            cacheManager.get('stats-test');
            
            // Miss
            cacheManager.get('non-existent');
            
            const stats = cacheManager.getStats();
            expect(stats.totalItems).toBe(1);
            expect(stats.hitRate).toBeGreaterThan(0);
            expect(stats.missRate).toBeGreaterThan(0);
        });

        it('should calculate hit and miss rates correctly', () => {
            cacheManager.set('hit-test', 'value');
            
            // 2 hits
            cacheManager.get('hit-test');
            cacheManager.get('hit-test');
            
            // 1 miss
            cacheManager.get('non-existent');
            
            const stats = cacheManager.getStats();
            expect(stats.hitRate).toBeCloseTo(2/3, 2);
            expect(stats.missRate).toBeCloseTo(1/3, 2);
        });
    });

    describe('Configuration', () => {
        it('should update configuration', () => {
            const newConfig: Partial<CacheConfig> = {
                maxCacheSize: 2000,
                defaultTTL: 5000
            };
            
            cacheManager.updateConfig(newConfig);
            
            const config = cacheManager.getConfig();
            expect(config.maxCacheSize).toBe(2000);
            expect(config.defaultTTL).toBe(5000);
        });

        it('should trigger cleanup when max size is reduced', () => {
            const largeData = 'x'.repeat(1000);
            cacheManager.set('large1', largeData);
            cacheManager.set('large2', largeData);
            
            // Reduce max cache size
            cacheManager.updateConfig({ maxCacheSize: 1500 });
            
            // Should have evicted at least one item
            const itemCount = cacheManager.count();
            expect(itemCount).toBeLessThan(2);
        });
    });

    describe('Import/Export', () => {
        it('should export cache data', () => {
            cacheManager.set('export-test1', 'value1');
            cacheManager.set('export-test2', { data: 'value2' });
            
            const exported = cacheManager.export();
            expect(typeof exported).toBe('string');
            
            const parsed = JSON.parse(exported);
            expect(parsed).toHaveProperty('config');
            expect(parsed).toHaveProperty('items');
            expect(parsed).toHaveProperty('stats');
            expect(parsed).toHaveProperty('timestamp');
        });

        it('should import cache data', () => {
            // Set up initial data
            cacheManager.set('import-test1', 'value1');
            cacheManager.set('import-test2', 'value2');
            
            const exported = cacheManager.export();
            
            // Clear cache
            cacheManager.clear();
            expect(cacheManager.count()).toBe(0);
            
            // Import data
            const success = cacheManager.import(exported);
            expect(success).toBe(true);
            
            expect(cacheManager.get('import-test1')).toBe('value1');
            expect(cacheManager.get('import-test2')).toBe('value2');
        });

        it('should handle invalid import data', () => {
            const success = cacheManager.import('invalid json');
            expect(success).toBe(false);
        });

        it('should skip expired items during import', async () => {
            // Create item with short TTL
            cacheManager.set('expire-on-import', 'value', 50);
            
            const exported = cacheManager.export();
            
            // Wait for expiration
            await new Promise(resolve => setTimeout(resolve, 100));
            
            cacheManager.clear();
            const success = cacheManager.import(exported);
            
            expect(success).toBe(true);
            expect(cacheManager.has('expire-on-import')).toBe(false);
        });
    });

    describe('Key Management', () => {
        it('should return all cache keys', () => {
            cacheManager.set('key1', 'value1');
            cacheManager.set('key2', 'value2');
            cacheManager.set('key3', 'value3');
            
            const keys = cacheManager.keys();
            expect(keys).toHaveLength(3);
            expect(keys).toContain('key1');
            expect(keys).toContain('key2');
            expect(keys).toContain('key3');
        });

        it('should handle empty cache keys', () => {
            const keys = cacheManager.keys();
            expect(keys).toHaveLength(0);
        });
    });

    describe('Error Handling', () => {
        it('should handle storage errors gracefully', () => {
            // Mock storage error
            mockLocalStorage.setItem.mockImplementation(() => {
                throw new Error('Storage error');
            });
            
            // Should not throw
            expect(() => {
                cacheManager.set('error-test', 'value');
            }).not.toThrow();
        });

        it('should handle retrieval errors gracefully', () => {
            // Mock storage error
            mockLocalStorage.getItem.mockImplementation(() => {
                throw new Error('Retrieval error');
            });
            
            // Should return null instead of throwing
            const result = cacheManager.get('any-key');
            expect(result).toBeNull();
        });
    });

    describe('Memory Management', () => {
        it('should cleanup resources on destroy', () => {
            cacheManager.set('cleanup-test', 'value');
            expect(cacheManager.count()).toBe(1);
            
            cacheManager.destroy();
            expect(cacheManager.count()).toBe(0);
        });

        it('should handle large datasets efficiently', () => {
            const startTime = Date.now();
            
            // Add many items
            for (let i = 0; i < 1000; i++) {
                cacheManager.set(`item_${i}`, { index: i, data: `data_${i}` });
            }
            
            const endTime = Date.now();
            const duration = endTime - startTime;
            
            // Should complete within reasonable time
            expect(duration).toBeLessThan(1000); // Less than 1 second
            expect(cacheManager.count()).toBe(1000);
        });
    });
});