
---

# 🤝 `CONTRIBUTING.md`

```markdown
# 🤝 Contributing to Spiron

Thank you for your interest in contributing to **Spiron**!

Spiron is still in its early stage — we welcome help on code, math modeling, documentation,  
and performance tuning.

---

## 🧩 Project Structure

| Folder | Purpose |
|---------|----------|
| `/src/main/java/com/spiron/core` | Engine logic & math |
| `/src/main/java/com/spiron/network` | gRPC networking |
| `/src/main/java/com/spiron/security` | BLS cryptography |
| `/src/main/java/com/spiron/metrics` | Metrics system |
| `/src/main/java/com/spiron/api` | Client & admin APIs |
| `/src/test/java` | Unit + integration tests |
| `/k8s` | K8s deployment manifests |

---

## 🧪 Running Tests

Unit tests:
```bash
./gradlew test
