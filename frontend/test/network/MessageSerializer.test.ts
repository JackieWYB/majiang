import { describe, it, expect } from 'vitest';
import { MessageSerializer } from '../../assets/scripts/network/MessageSerializer';
import { GameMessage } from '../../assets/scripts/network/NetworkManager';

describe('MessageSerializer', () => {
  describe('Serialization', () => {
    it('should serialize valid message', () => {
      const message: GameMessage = {
        type: 'REQ',
        cmd: 'TEST_COMMAND',
        reqId: 'req_123',
        roomId: 'room_456',
        data: { test: 'data' },
        timestamp: 1234567890
      };

      const serialized = MessageSerializer.serialize(message);
      const parsed = JSON.parse(serialized);

      expect(parsed.version).toBe('1.0');
      expect(parsed.type).toBe('REQ');
      expect(parsed.cmd).toBe('TEST_COMMAND');
      expect(parsed.reqId).toBe('req_123');
      expect(parsed.roomId).toBe('room_456');
      expect(parsed.data).toEqual({ test: 'data' });
      expect(parsed.timestamp).toBe(1234567890);
    });

    it('should add timestamp if missing', () => {
      const message: GameMessage = {
        type: 'EVENT',
        cmd: 'TEST_EVENT'
      } as any;

      const serialized = MessageSerializer.serialize(message);
      const parsed = JSON.parse(serialized);

      expect(parsed.timestamp).toBeTypeOf('number');
      expect(parsed.timestamp).toBeGreaterThan(0);
    });

    it('should throw error for invalid message type', () => {
      const message = {
        type: 'INVALID',
        cmd: 'TEST_COMMAND',
        timestamp: Date.now()
      } as any;

      expect(() => MessageSerializer.serialize(message)).toThrow('Invalid message type');
    });

    it('should throw error for missing command', () => {
      const message = {
        type: 'REQ',
        timestamp: Date.now()
      } as any;

      expect(() => MessageSerializer.serialize(message)).toThrow('Invalid command');
    });

    it('should throw error for oversized message', () => {
      const largeData = 'x'.repeat(65 * 1024); // 65KB
      const message: GameMessage = {
        type: 'REQ',
        cmd: 'TEST_COMMAND',
        data: largeData,
        timestamp: Date.now()
      };

      expect(() => MessageSerializer.serialize(message)).toThrow('Message too large');
    });
  });

  describe('Deserialization', () => {
    it('should deserialize valid message', () => {
      const originalMessage: GameMessage = {
        type: 'RESP',
        cmd: 'TEST_RESPONSE',
        reqId: 'req_123',
        data: { result: 'success' },
        timestamp: 1234567890
      };

      const serialized = MessageSerializer.serialize(originalMessage);
      const deserialized = MessageSerializer.deserialize(serialized);

      expect(deserialized.type).toBe('RESP');
      expect(deserialized.cmd).toBe('TEST_RESPONSE');
      expect(deserialized.reqId).toBe('req_123');
      expect(deserialized.data).toEqual({ result: 'success' });
      expect(deserialized.timestamp).toBe(1234567890);
    });

    it('should handle version compatibility', () => {
      const messageWithVersion = {
        version: '1.0',
        type: 'EVENT',
        cmd: 'TEST_EVENT',
        timestamp: Date.now()
      };

      const serialized = JSON.stringify(messageWithVersion);
      
      expect(() => MessageSerializer.deserialize(serialized)).not.toThrow();
    });

    it('should warn about version mismatch', () => {
      const messageWithOldVersion = {
        version: '0.9',
        type: 'EVENT',
        cmd: 'TEST_EVENT',
        timestamp: Date.now()
      };

      const serialized = JSON.stringify(messageWithOldVersion);
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      
      MessageSerializer.deserialize(serialized);
      
      expect(consoleSpy).toHaveBeenCalledWith(
        expect.stringContaining('Message version mismatch')
      );
    });

    it('should throw error for invalid JSON', () => {
      expect(() => MessageSerializer.deserialize('invalid json')).toThrow('Failed to deserialize message');
    });

    it('should throw error for invalid message structure', () => {
      const invalidMessage = JSON.stringify({
        type: 'INVALID_TYPE',
        cmd: 'TEST_COMMAND',
        timestamp: Date.now()
      });

      expect(() => MessageSerializer.deserialize(invalidMessage)).toThrow('Invalid message type');
    });
  });

  describe('Message Creation Helpers', () => {
    it('should create request message', () => {
      const message = MessageSerializer.createRequest('TEST_REQUEST', { param: 'value' }, 'room_123');

      expect(message.type).toBe('REQ');
      expect(message.cmd).toBe('TEST_REQUEST');
      expect(message.reqId).toMatch(/^req_\d+_[a-z0-9]+$/);
      expect(message.roomId).toBe('room_123');
      expect(message.data).toEqual({ param: 'value' });
      expect(message.timestamp).toBeTypeOf('number');
    });

    it('should create response message', () => {
      const message = MessageSerializer.createResponse('TEST_RESPONSE', 'req_123', { result: 'ok' }, 'room_456');

      expect(message.type).toBe('RESP');
      expect(message.cmd).toBe('TEST_RESPONSE');
      expect(message.reqId).toBe('req_123');
      expect(message.roomId).toBe('room_456');
      expect(message.data).toEqual({ result: 'ok' });
      expect(message.timestamp).toBeTypeOf('number');
    });

    it('should create event message', () => {
      const message = MessageSerializer.createEvent('TEST_EVENT', { info: 'test' }, 'room_789');

      expect(message.type).toBe('EVENT');
      expect(message.cmd).toBe('TEST_EVENT');
      expect(message.reqId).toBeUndefined();
      expect(message.roomId).toBe('room_789');
      expect(message.data).toEqual({ info: 'test' });
      expect(message.timestamp).toBeTypeOf('number');
    });

    it('should create error message with Error object', () => {
      const error = new Error('Test error');
      const message = MessageSerializer.createError('TEST_ERROR', error, 'req_123', 'room_456');

      expect(message.type).toBe('ERROR');
      expect(message.cmd).toBe('TEST_ERROR');
      expect(message.reqId).toBe('req_123');
      expect(message.roomId).toBe('room_456');
      expect(message.data).toEqual({
        message: 'Test error',
        code: 'Error'
      });
      expect(message.timestamp).toBeTypeOf('number');
    });

    it('should create error message with string', () => {
      const message = MessageSerializer.createError('TEST_ERROR', 'Simple error message');

      expect(message.type).toBe('ERROR');
      expect(message.cmd).toBe('TEST_ERROR');
      expect(message.data).toEqual({
        message: 'Simple error message',
        code: 'UNKNOWN_ERROR'
      });
    });
  });

  describe('Compression', () => {
    it('should compress and decompress data', () => {
      const testData = 'This is test data for compression';
      
      const compressed = MessageSerializer.compress(testData);
      const decompressed = MessageSerializer.decompress(compressed);

      expect(decompressed).toBe(testData);
    });
  });

  describe('Request ID Generation', () => {
    it('should generate unique request IDs', () => {
      const message1 = MessageSerializer.createRequest('TEST1');
      const message2 = MessageSerializer.createRequest('TEST2');

      expect(message1.reqId).not.toBe(message2.reqId);
      expect(message1.reqId).toMatch(/^req_\d+_[a-z0-9]+$/);
      expect(message2.reqId).toMatch(/^req_\d+_[a-z0-9]+$/);
    });
  });
});