// Mock Cocos Creator framework for testing

export const _decorator = {
  ccclass: (name?: string) => (target: any) => target,
  property: (options?: any) => (target: any, propertyKey: string) => {}
};

export class Component {
  node: any = {
    destroy: vi.fn()
  };

  onLoad() {}
  onDestroy() {}
}

export class Node {
  destroy = vi.fn();
}