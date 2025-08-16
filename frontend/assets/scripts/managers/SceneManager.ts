import { _decorator, Component, director } from 'cc';
const { ccclass, property } = _decorator;

/**
 * Scene Manager for handling scene transitions
 */
@ccclass('SceneManager')
export class SceneManager extends Component {
    private static _instance: SceneManager;

    public static get instance(): SceneManager {
        return this._instance;
    }

    onLoad() {
        if (SceneManager._instance) {
            this.node.destroy();
            return;
        }
        SceneManager._instance = this;
        director.addPersistRootNode(this.node);
    }

    /**
     * Load scene by name
     * @param sceneName Name of the scene to load
     */
    public loadScene(sceneName: string): void {
        director.loadScene(sceneName);
    }

    /**
     * Preload scene for faster loading
     * @param sceneName Name of the scene to preload
     */
    public preloadScene(sceneName: string): Promise<void> {
        return new Promise((resolve, reject) => {
            director.preloadScene(sceneName, (error) => {
                if (error) {
                    reject(error);
                } else {
                    resolve();
                }
            });
        });
    }

    /**
     * Get current scene name
     */
    public getCurrentScene(): string {
        return director.getScene()?.name || '';
    }
}