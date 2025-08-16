import { _decorator, Component, Node } from 'cc';
const { ccclass, property } = _decorator;

/**
 * Base UI Controller class for all UI components
 */
@ccclass('BaseUIController')
export class BaseUIController extends Component {
    @property(Node)
    protected rootNode: Node = null!;

    protected _isInitialized: boolean = false;

    onLoad() {
        this.initializeUI();
    }

    /**
     * Initialize UI components
     */
    protected initializeUI(): void {
        if (this._isInitialized) {
            return;
        }

        this.setupComponents();
        this.bindEvents();
        this._isInitialized = true;
    }

    /**
     * Setup UI components - override in subclasses
     */
    protected setupComponents(): void {
        // Override in subclasses
    }

    /**
     * Bind UI events - override in subclasses
     */
    protected bindEvents(): void {
        // Override in subclasses
    }

    /**
     * Show UI
     */
    public show(): void {
        if (this.rootNode) {
            this.rootNode.active = true;
        } else {
            this.node.active = true;
        }
        this.onShow();
    }

    /**
     * Hide UI
     */
    public hide(): void {
        if (this.rootNode) {
            this.rootNode.active = false;
        } else {
            this.node.active = false;
        }
        this.onHide();
    }

    /**
     * Called when UI is shown - override in subclasses
     */
    protected onShow(): void {
        // Override in subclasses
    }

    /**
     * Called when UI is hidden - override in subclasses
     */
    protected onHide(): void {
        // Override in subclasses
    }

    /**
     * Cleanup resources - override in subclasses
     */
    public cleanup(): void {
        this.unbindEvents();
    }

    /**
     * Unbind UI events - override in subclasses
     */
    protected unbindEvents(): void {
        // Override in subclasses
    }

    onDestroy() {
        this.cleanup();
    }
}