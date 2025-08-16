import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { NetworkManager, GameMessage, ConnectionError, TimeoutError, AuthenticationError } from '../../assets/scripts/network/NetworkManager';
import { MockWebSocket } from '../mocks/websocket';
import { mockWx } from '../mocks/wechat';

describe('NetworkManager', () => {
  let networkManager: NetworkManager;
  let mockSocket: MockWebSocket;

  beforeEach(() => {
    // Clear any existing instance
    (NetworkManager as any)._instance = null;
    networkManager = new NetworkManager();
    networkManager.onLoad();
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    networkManager.disconnect();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  describe('Connection Management', () => {
    it('should connect successfully with valid token', async () => {
      const token = 'valid-token';
      const connectPromise = networkManager.connect(token);

      // Wait for connection to establish
      await connectPromise;

      expect(networkManager.isConnected()).toBe(true);
      expect(networkManager.getAuthToken()).toBe(token);
    });

    it('should handle connection timeout', async () => {
      // Mock WebSocket that never opens
      vi.spyOn(global, 'WebSocket').mockImplementation(() => {
        const socket = new MockWebSocket('ws://test');
        socket.readyState = MockWebSocket.CONNECTING;
        // Don't trigger onopen
        return socket as any;
      });

      const token = 'valid-token';
      
      await expect(networkManager.connect(token)).rejects.toThrow(TimeoutError);
    });

    it('should handle connection error', async () => {
      vi.spyOn(global, 'WebSocket').mockImplementation(() => {
        const socket = new MockWebSocket('ws://test');
        setTimeout(() => socket.simulateError(), 5);
        return socket as any;
      });

      const token = 'valid-token';
      
      await expect(networkManager.connect(token)).rejects.toThrow(ConnectionError);
    });

    it('should disconnect properly', async () => {
      await networkManager.connect('test-token');
      expect(networkManager.isConnected()).toBe(true);

      networkManager.disconnect();
      expect(networkManager.isConnected()).toBe(false);
    });
  });

  describe('Message Handling', () => {
    beforeEach(async () => {
      await networkManager.connect('test-token');
    });

    it('should send messages successfully', () => {
      const message: GameMessage = {
        type: 'REQ',
        cmd: 'TEST_COMMAND',
        timestamp: Date.now()
      };

      expect(() => networkManager.sendMessage(message)).not.toThrow();
    });

    it('should throw error when sending message while disconnected', () => {
      networkManager.disconnect();
      
      const message: GameMessage = {
        type: 'REQ',
        cmd: 'TEST_COMMAND',
        timestamp: Date.now()
      };

      expect(() => networkManager.sendMessage(message)).toThrow(ConnectionError);
    });

    it('should handle incoming messages', async () => {
      const handler = vi.fn();
      networkManager.onMessage('TEST_EVENT', handler);

      // Simulate incoming message
      const mockSocket = (networkManager as any)._socket as MockWebSocket;
      const testMessage: GameMessage = {
        type: 'EVENT',
        cmd: 'TEST_EVENT',
        data: { test: 'data' },
        timestamp: Date.now()
      };

      mockSocket.simulateMessage(JSON.stringify(testMessage));

      expect(handler).toHaveBeenCalledWith(testMessage);
    });

    it('should handle request-response pattern', async () => {
      const requestMessage: GameMessage = {
        type: 'REQ',
        cmd: 'TEST_REQUEST',
        timestamp: Date.now()
      };

      const responsePromise = networkManager.sendRequest(requestMessage);

      // Simulate response
      const mockSocket = (networkManager as any)._socket as MockWebSocket;
      const responseMessage: GameMessage = {
        type: 'RESP',
        cmd: 'TEST_REQUEST',
        reqId: requestMessage.reqId,
        data: { result: 'success' },
        timestamp: Date.now()
      };

      setTimeout(() => {
        mockSocket.simulateMessage(JSON.stringify(responseMessage));
      }, 10);

      const result = await responsePromise;
      expect(result).toEqual({ result: 'success' });
    });

    it('should handle request timeout', async () => {
      const requestMessage: GameMessage = {
        type: 'REQ',
        cmd: 'TEST_REQUEST',
        timestamp: Date.now()
      };

      await expect(networkManager.sendRequest(requestMessage, 100)).rejects.toThrow(TimeoutError);
    });
  });

  describe('Heartbeat Mechanism', () => {
    beforeEach(async () => {
      await networkManager.connect('test-token');
    });

    it('should send heartbeat messages', async () => {
      const sendMessageSpy = vi.spyOn(networkManager, 'sendMessage');
      
      // Fast-forward time to trigger heartbeat
      vi.advanceTimersByTime(30000);

      expect(sendMessageSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'REQ',
          cmd: 'HEARTBEAT'
        })
      );
    });

    it('should handle heartbeat responses', async () => {
      const mockSocket = (networkManager as any)._socket as MockWebSocket;
      const heartbeatResponse: GameMessage = {
        type: 'RESP',
        cmd: 'HEARTBEAT',
        timestamp: Date.now()
      };

      // Should not throw or cause issues
      expect(() => {
        mockSocket.simulateMessage(JSON.stringify(heartbeatResponse));
      }).not.toThrow();
    });
  });

  describe('Reconnection Logic', () => {
    it('should attempt reconnection on connection loss', async () => {
      await networkManager.connect('test-token');
      const connectSpy = vi.spyOn(networkManager, 'connect');

      // Simulate connection loss
      const mockSocket = (networkManager as any)._socket as MockWebSocket;
      mockSocket.simulateClose(1006, 'Connection lost');

      // Fast-forward time to trigger reconnection
      vi.advanceTimersByTime(1000);

      expect(connectSpy).toHaveBeenCalled();
    });

    it('should use exponential backoff for reconnection attempts', async () => {
      await networkManager.connect('test-token');
      
      // Mock connect to always fail
      vi.spyOn(networkManager, 'connect').mockRejectedValue(new ConnectionError('Connection failed'));

      const mockSocket = (networkManager as any)._socket as MockWebSocket;
      mockSocket.simulateClose(1006, 'Connection lost');

      // First attempt after 1 second
      vi.advanceTimersByTime(1000);
      expect(networkManager.getConnectionState().reconnectAttempts).toBe(1);

      // Second attempt after 2 seconds (exponential backoff)
      vi.advanceTimersByTime(2000);
      expect(networkManager.getConnectionState().reconnectAttempts).toBe(2);
    });

    it('should stop reconnection after max attempts', async () => {
      await networkManager.connect('test-token');
      
      // Mock connect to always fail
      vi.spyOn(networkManager, 'connect').mockRejectedValue(new ConnectionError('Connection failed'));

      const mockSocket = (networkManager as any)._socket as MockWebSocket;
      mockSocket.simulateClose(1006, 'Connection lost');

      // Simulate max reconnection attempts
      for (let i = 0; i < 5; i++) {
        vi.advanceTimersByTime(Math.pow(2, i) * 1000);
      }

      expect(networkManager.getConnectionState().reconnectAttempts).toBe(5);
    });

    it('should force reconnection', async () => {
      await networkManager.connect('test-token');
      networkManager.disconnect();

      const connectSpy = vi.spyOn(networkManager, 'connect');
      await networkManager.forceReconnect();

      expect(connectSpy).toHaveBeenCalledWith('test-token');
      expect(networkManager.getConnectionState().reconnectAttempts).toBe(0);
    });
  });

  describe('Error Handling', () => {
    it('should handle malformed messages gracefully', async () => {
      await networkManager.connect('test-token');
      
      const mockSocket = (networkManager as any)._socket as MockWebSocket;
      
      // Should not throw
      expect(() => {
        mockSocket.simulateMessage('invalid json');
      }).not.toThrow();
    });

    it('should handle message handler errors gracefully', async () => {
      await networkManager.connect('test-token');
      
      const faultyHandler = vi.fn(() => {
        throw new Error('Handler error');
      });
      
      networkManager.onMessage('TEST_EVENT', faultyHandler);

      const mockSocket = (networkManager as any)._socket as MockWebSocket;
      const testMessage: GameMessage = {
        type: 'EVENT',
        cmd: 'TEST_EVENT',
        timestamp: Date.now()
      };

      // Should not throw
      expect(() => {
        mockSocket.simulateMessage(JSON.stringify(testMessage));
      }).not.toThrow();

      expect(faultyHandler).toHaveBeenCalled();
    });
  });

  describe('WeChat Integration', () => {
    beforeEach(() => {
      // Enable WeChat mode
      (global as any).wx = mockWx;
    });

    it('should use WeChat WebSocket API when available', async () => {
      const connectSocketSpy = vi.spyOn(mockWx, 'connectSocket');
      
      await networkManager.connect('test-token');

      expect(connectSocketSpy).toHaveBeenCalledWith({
        url: expect.stringContaining('test-token'),
        protocols: ['websocket']
      });
    });
  });
});