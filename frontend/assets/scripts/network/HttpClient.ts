import { NetworkManager, HttpRequestOptions, HttpResponse, NetworkError, AuthenticationError, TimeoutError } from './NetworkManager';

/**
 * HTTP Client utility for REST API calls
 * Provides convenient methods for common HTTP operations
 */
export class HttpClient {
    private networkManager: NetworkManager;

    constructor(networkManager: NetworkManager) {
        this.networkManager = networkManager;
    }

    /**
     * GET request
     */
    public async get<T = any>(endpoint: string, timeout?: number): Promise<T> {
        const response = await this.networkManager.httpRequest<T>(endpoint, {
            method: 'GET',
            timeout
        });
        return response.data;
    }

    /**
     * POST request
     */
    public async post<T = any>(endpoint: string, data?: any, timeout?: number): Promise<T> {
        const response = await this.networkManager.httpRequest<T>(endpoint, {
            method: 'POST',
            body: data,
            timeout
        });
        return response.data;
    }

    /**
     * PUT request
     */
    public async put<T = any>(endpoint: string, data?: any, timeout?: number): Promise<T> {
        const response = await this.networkManager.httpRequest<T>(endpoint, {
            method: 'PUT',
            body: data,
            timeout
        });
        return response.data;
    }

    /**
     * DELETE request
     */
    public async delete<T = any>(endpoint: string, timeout?: number): Promise<T> {
        const response = await this.networkManager.httpRequest<T>(endpoint, {
            method: 'DELETE',
            timeout
        });
        return response.data;
    }

    /**
     * Upload file (for WeChat Mini Program)
     */
    public async uploadFile(endpoint: string, filePath: string, name: string = 'file', formData?: Record<string, any>): Promise<any> {
        return new Promise((resolve, reject) => {
            if (typeof wx === 'undefined') {
                reject(new NetworkError('File upload only supported in WeChat Mini Program', 'UNSUPPORTED_OPERATION'));
                return;
            }

            const headers: Record<string, string> = {};
            const token = this.networkManager.getAuthToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }

            wx.uploadFile({
                url: `${this.networkManager.getConfig().httpBaseUrl}${endpoint}`,
                filePath,
                name,
                header: headers,
                formData,
                success: (res) => {
                    try {
                        const data = JSON.parse(res.data);
                        if (res.statusCode >= 200 && res.statusCode < 300) {
                            resolve(data);
                        } else if (res.statusCode === 401) {
                            reject(new AuthenticationError('Authentication failed'));
                        } else {
                            reject(new NetworkError(`HTTP ${res.statusCode}`, 'HTTP_ERROR', data));
                        }
                    } catch (error) {
                        reject(new NetworkError('Failed to parse upload response', 'PARSE_ERROR', error));
                    }
                },
                fail: (error) => {
                    reject(new NetworkError('File upload failed', 'UPLOAD_ERROR', error));
                }
            });
        });
    }

    /**
     * Download file (for WeChat Mini Program)
     */
    public async downloadFile(endpoint: string, timeout?: number): Promise<string> {
        return new Promise((resolve, reject) => {
            if (typeof wx === 'undefined') {
                reject(new NetworkError('File download only supported in WeChat Mini Program', 'UNSUPPORTED_OPERATION'));
                return;
            }

            const headers: Record<string, string> = {};
            const token = this.networkManager.getAuthToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }

            const timeoutTimer = timeout ? setTimeout(() => {
                reject(new TimeoutError('Download timeout'));
            }, timeout) : null;

            wx.downloadFile({
                url: `${this.networkManager.getConfig().httpBaseUrl}${endpoint}`,
                header: headers,
                success: (res) => {
                    if (timeoutTimer) clearTimeout(timeoutTimer);
                    if (res.statusCode >= 200 && res.statusCode < 300) {
                        resolve(res.tempFilePath);
                    } else if (res.statusCode === 401) {
                        reject(new AuthenticationError('Authentication failed'));
                    } else {
                        reject(new NetworkError(`HTTP ${res.statusCode}`, 'HTTP_ERROR'));
                    }
                },
                fail: (error) => {
                    if (timeoutTimer) clearTimeout(timeoutTimer);
                    reject(new NetworkError('File download failed', 'DOWNLOAD_ERROR', error));
                }
            });
        });
    }
}