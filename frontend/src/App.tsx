import styles from "./App.module.css";

export function App() {
  return (
    <main className={styles.shell}>
      <h1 className={styles.title}>RoseCloud IAM</h1>
      <p className={styles.copy}>
        骨架已就绪。后续切片将补齐 Operator setup 与租户流程。
      </p>
    </main>
  );
}
