import { describe, it, expect, beforeEach, vi } from 'vitest';
import { HttpClient } from '../../assets/scripts/network/HttpClient';
import { NetworkManager, AuthenticationError, NetworkError } from '../../assets/scripts/network/NetworkManager';
import { mockWx } from '../mocks/wechat';

describe('HttpClient', () => {
  let httpClient: HttpClient;
  let networkManager: NetworkManager;

  beforeEach(() => {
    networkManager = new NetworkManager();
    networkManager.onLoad();
    httpClient = new HttpClient(networkManager);
    vi.clearAllMocks();
  });

  describe('HTTP Methods', () => {
    it('should make GET request', async () => {
      const httpRequestSpy = vi.spyOn(networkManager, 'httpRequest').mockResolvedValue({
        data: { result: 'success' },
        status: 200,
        statusText: 'OK',
        headers: {}
      });

      const result = await httpClient.get('/test-endpoint');

      expect(httpRequestSpy).toHaveBeenCalledWith('/test-endpoint', {
        method: 'GET',
        timeout: undefined
      });
      expect(result).toEqual({ result: 'success' });
    });

    it('should make POST request with data', async () => {
      const testData = { name: 'test' };
      const httpRequestSpy = vi.spyOn(networkManager, 'httpRequest').mockResolvedValue({
        data: { id: 1, ...testData },
        status: 201,
        statusText: 'Created',
        headers: {}
      });

      const result = await httpClient.post('/test-endpoint', testData);

      expect(httpRequestSpy).toHaveBeenCalledWith('/test-endpoint', {
        method: 'POST',
        body: testData,
        timeout: undefined
      });
      expect(result).toEqual({ id: 1, ...testData });
    });

    it('should make PUT request', async () => {
      const testData = { name: 'updated' };
      const httpRequestSpy = vi.spyOn(networkManager, 'httpRequest').mockResolvedValue({
        data: testData,
        status: 200,
        statusText: 'OK',
        headers: {}
      });

      const result = await httpClient.put('/test-endpoint', testData);

      expect(httpRequestSpy).toHaveBeenCalledWith('/test-endpoint', {
        method: 'PUT',
        body: testData,
        timeout: undefined
      });
      expect(result).toEqual(testData);
    });

    it('should make DELETE request', async () => {
      const httpRequestSpy = vi.spyOn(networkManager, 'httpRequest').mockResolvedValue({
        data: { deleted: true },
        status: 200,
        statusText: 'OK',
        headers: {}
      });

      const result = await httpClient.delete('/test-endpoint');

      expect(httpRequestSpy).toHaveBeenCalledWith('/test-endpoint', {
        method: 'DELETE',
        timeout: undefined
      });
      expect(result).toEqual({ deleted: true });
    });

    it('should handle custom timeout', async () => {
      const httpRequestSpy = vi.spyOn(networkManager, 'httpRequest').mockResolvedValue({
        data: {},
        status: 200,
        statusText: 'OK',
        headers: {}
      });

      await httpClient.get('/test-endpoint', 5000);

      expect(httpRequestSpy).toHaveBeenCalledWith('/test-endpoint', {
        method: 'GET',
        timeout: 5000
      });
    });
  });

  describe('WeChat File Operations', () => {
    beforeEach(() => {
      (global as any).wx = mockWx;
      networkManager.setAuthToken('test-token');
    });

    it('should upload file successfully', async () => {
      const uploadFileSpy = vi.spyOn(mockWx, 'uploadFile').mockImplementation((options) => {
        setTimeout(() => {
          if (options.success) {
            options.success({
              data: JSON.stringify({ fileId: 'uploaded-file-id' }),
              statusCode: 200
            });
          }
        }, 10);
      });

      const result = await httpClient.uploadFile('/upload', '/path/to/file.jpg', 'image');

      expect(uploadFileSpy).toHaveBeenCalledWith({
        url: expect.stringContaining('/upload'),
        filePath: '/path/to/file.jpg',
        name: 'image',
        header: {
          'Authorization': 'Bearer test-token'
        },
        formData: undefined
      });
      expect(result).toEqual({ fileId: 'uploaded-file-id' });
    });

    it('should handle upload failure', async () => {
      vi.spyOn(mockWx, 'uploadFile').mockImplementation((options) => {
        setTimeout(() => {
          if (options.fail) {
            options.fail({ errMsg: 'Upload failed' });
          }
        }, 10);
      });

      await expect(httpClient.uploadFile('/upload', '/path/to/file.jpg')).rejects.toThrow(NetworkError);
    });

    it('should handle upload authentication error', async () => {
      vi.spyOn(mockWx, 'uploadFile').mockImplementation((options) => {
        setTimeout(() => {
          if (options.success) {
            options.success({
              data: JSON.stringify({ error: 'Unauthorized' }),
              statusCode: 401
            });
          }
        }, 10);
      });

      await expect(httpClient.uploadFile('/upload', '/path/to/file.jpg')).rejects.toThrow(AuthenticationError);
    });

    it('should download file successfully', async () => {
      const downloadFileSpy = vi.spyOn(mockWx, 'downloadFile').mockImplementation((options) => {
        setTimeout(() => {
          if (options.success) {
            options.success({
              tempFilePath: '/tmp/downloaded-file',
              statusCode: 200
            });
          }
        }, 10);
      });

      const result = await httpClient.downloadFile('/download/file.jpg');

      expect(downloadFileSpy).toHaveBeenCalledWith({
        url: expect.stringContaining('/download/file.jpg'),
        header: {
          'Authorization': 'Bearer test-token'
        }
      });
      expect(result).toBe('/tmp/downloaded-file');
    });

    it('should handle download failure', async () => {
      vi.spyOn(mockWx, 'downloadFile').mockImplementation((options) => {
        setTimeout(() => {
          if (options.fail) {
            options.fail({ errMsg: 'Download failed' });
          }
        }, 10);
      });

      await expect(httpClient.downloadFile('/download/file.jpg')).rejects.toThrow(NetworkError);
    });

    it('should throw error for file operations outside WeChat', async () => {
      delete (global as any).wx;

      await expect(httpClient.uploadFile('/upload', '/path/to/file.jpg')).rejects.toThrow(NetworkError);
      await expect(httpClient.downloadFile('/download/file.jpg')).rejects.toThrow(NetworkError);
    });
  });
});