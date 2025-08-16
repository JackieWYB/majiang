// Test setup file
import './mocks/websocket';
import './mocks/wechat';

// Global test utilities
global.console = {
  ...console,
  // Suppress console.log in tests unless needed
  log: vi.fn(),
  warn: vi.fn(),
  error: vi.fn()
};