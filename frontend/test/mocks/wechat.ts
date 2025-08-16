// Mock WeChat Mini Program APIs for testing

interface WxRequestOptions {
  url: string;
  method?: string;
  data?: any;
  header?: Record<string, string>;
  success?: (res: any) => void;
  fail?: (error: any) => void;
}

interface WxSocketOptions {
  url: string;
  protocols?: string[];
}

interface WxUploadOptions {
  url: string;
  filePath: string;
  name: string;
  header?: Record<string, string>;
  formData?: Record<string, any>;
  success?: (res: any) => void;
  fail?: (error: any) => void;
}

interface WxDownloadOptions {
  url: string;
  header?: Record<string, string>;
  success?: (res: any) => void;
  fail?: (error: any) => void;
}

class MockWxSocket {
  public onOpen: (() => void) | null = null;
  public onClose: ((event: any) => void) | null = null;
  public onMessage: ((event: any) => void) | null = null;
  public onError: ((error: any) => void) | null = null;

  constructor(options: WxSocketOptions) {
    // Simulate connection
    setTimeout(() => {
      if (this.onOpen) {
        this.onOpen();
      }
    }, 10);
  }

  send(options: { data: string }): void {
    // Mock sending
  }

  close(): void {
    setTimeout(() => {
      if (this.onClose) {
        this.onClose({ code: 1000, reason: '' });
      }
    }, 10);
  }

  // Test utilities
  simulateMessage(data: string): void {
    if (this.onMessage) {
      this.onMessage({ data });
    }
  }

  simulateError(error: any): void {
    if (this.onError) {
      this.onError(error);
    }
  }

  simulateClose(code: number = 1000, reason: string = ''): void {
    if (this.onClose) {
      this.onClose({ code, reason });
    }
  }
}

const mockWx = {
  request: vi.fn((options: WxRequestOptions) => {
    // Mock successful response by default
    setTimeout(() => {
      if (options.success) {
        options.success({
          data: { success: true },
          statusCode: 200,
          header: {}
        });
      }
    }, 10);
  }),

  connectSocket: vi.fn((options: WxSocketOptions) => {
    return new MockWxSocket(options);
  }),

  uploadFile: vi.fn((options: WxUploadOptions) => {
    setTimeout(() => {
      if (options.success) {
        options.success({
          data: JSON.stringify({ success: true }),
          statusCode: 200
        });
      }
    }, 10);
  }),

  downloadFile: vi.fn((options: WxDownloadOptions) => {
    setTimeout(() => {
      if (options.success) {
        options.success({
          tempFilePath: '/tmp/downloaded-file',
          statusCode: 200
        });
      }
    }, 10);
  })
};

// Make wx available globally for tests
(global as any).wx = mockWx;

export { mockWx, MockWxSocket };