declare module "react" {
  export type ReactNode = unknown;
  export interface RefObject<T> { current: T | null; }
  export function createElement(type: unknown, props: unknown, ...children: unknown[]): unknown;
  export function useEffect(effect: () => void | (() => void), deps?: readonly unknown[]): void;
  export function useMemo<T>(factory: () => T, deps: readonly unknown[]): T;
  export function useRef<T>(value: T | null): RefObject<T>;
  export function useState<T>(value: T | (() => T)): [T, (value: T | ((previous:T)=>T)) => void];
  export function useSyncExternalStore<T>(subscribe: (notify:()=>void)=>()=>void, getSnapshot:()=>T, getServerSnapshot?:()=>T): T;
}
declare module "react-dom/client" { export interface Root { render(node: unknown): void; } export function createRoot(container: Element | DocumentFragment): Root; }
declare namespace JSX { interface IntrinsicElements { [name: string]: any; } }
