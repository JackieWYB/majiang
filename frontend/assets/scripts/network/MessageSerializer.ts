import { GameMessage } from './NetworkManager';

/**
 * Message serialization and deserialization utility
 * Handles message validation, compression, and format conversion
 */
export class MessageSerializer {
    private static readonly MESSAGE_VERSION = '1.0';
    private static readonly MAX_MESSAGE_SIZE = 64 * 1024; // 64KB

    /**
     * Serialize message to string
     */
    public static serialize(message: GameMessage): string {
        try {
            // Add version and validation
            const wrappedMessage = {
                version: this.MESSAGE_VERSION,
                ...message,
                timestamp: message.timestamp || Date.now()
            };

            // Validate message structure
            this.validateMessage(wrappedMessage);

            const serialized = JSON.stringify(wrappedMessage);

            // Check message size
            if (serialized.length > this.MAX_MESSAGE_SIZE) {
                throw new Error(`Message too large: ${serialized.length} bytes (max: ${this.MAX_MESSAGE_SIZE})`);
            }

            return serialized;
        } catch (error) {
            throw new Error(`Failed to serialize message: ${error.message}`);
        }
    }

    /**
     * Deserialize message from string
     */
    public static deserialize(data: string): GameMessage {
        try {
            const parsed = JSON.parse(data);

            // Validate version compatibility
            if (parsed.version && !this.isVersionCompatible(parsed.version)) {
                console.warn(`Message version mismatch: ${parsed.version} (current: ${this.MESSAGE_VERSION})`);
            }

            // Extract the actual message (remove wrapper)
            const message: GameMessage = {
                type: parsed.type,
                cmd: parsed.cmd,
                reqId: parsed.reqId,
                roomId: parsed.roomId,
                data: parsed.data,
                timestamp: parsed.timestamp
            };

            // Validate message structure
            this.validateMessage(message);

            return message;
        } catch (error) {
            throw new Error(`Failed to deserialize message: ${error.message}`);
        }
    }

    /**
     * Validate message structure
     */
    private static validateMessage(message: any): void {
        if (!message) {
            throw new Error('Message is null or undefined');
        }

        if (!message.type || !['EVENT', 'REQ', 'RESP', 'ERROR'].includes(message.type)) {
            throw new Error(`Invalid message type: ${message.type}`);
        }

        if (!message.cmd || typeof message.cmd !== 'string') {
            throw new Error(`Invalid command: ${message.cmd}`);
        }

        if (message.reqId && typeof message.reqId !== 'string') {
            throw new Error(`Invalid request ID: ${message.reqId}`);
        }

        if (message.roomId && typeof message.roomId !== 'string') {
            throw new Error(`Invalid room ID: ${message.roomId}`);
        }

        if (!message.timestamp || typeof message.timestamp !== 'number') {
            throw new Error(`Invalid timestamp: ${message.timestamp}`);
        }
    }

    /**
     * Check version compatibility
     */
    private static isVersionCompatible(version: string): boolean {
        const [major, minor] = version.split('.').map(Number);
        const [currentMajor, currentMinor] = this.MESSAGE_VERSION.split('.').map(Number);

        // Same major version is compatible
        return major === currentMajor;
    }

    /**
     * Create standardized request message
     */
    public static createRequest(cmd: string, data?: any, roomId?: string): GameMessage {
        return {
            type: 'REQ',
            cmd,
            reqId: this.generateRequestId(),
            roomId,
            data,
            timestamp: Date.now()
        };
    }

    /**
     * Create standardized response message
     */
    public static createResponse(cmd: string, reqId: string, data?: any, roomId?: string): GameMessage {
        return {
            type: 'RESP',
            cmd,
            reqId,
            roomId,
            data,
            timestamp: Date.now()
        };
    }

    /**
     * Create standardized event message
     */
    public static createEvent(cmd: string, data?: any, roomId?: string): GameMessage {
        return {
            type: 'EVENT',
            cmd,
            roomId,
            data,
            timestamp: Date.now()
        };
    }

    /**
     * Create standardized error message
     */
    public static createError(cmd: string, error: string | Error, reqId?: string, roomId?: string): GameMessage {
        const errorData = {
            message: error instanceof Error ? error.message : error,
            code: error instanceof Error ? error.name : 'UNKNOWN_ERROR'
        };

        return {
            type: 'ERROR',
            cmd,
            reqId,
            roomId,
            data: errorData,
            timestamp: Date.now()
        };
    }

    /**
     * Generate unique request ID
     */
    private static generateRequestId(): string {
        return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }

    /**
     * Compress message data (simple implementation)
     */
    public static compress(data: string): string {
        // Simple compression - in production, you might want to use a proper compression library
        return data;
    }

    /**
     * Decompress message data
     */
    public static decompress(data: string): string {
        // Simple decompression - in production, you might want to use a proper compression library
        return data;
    }
}