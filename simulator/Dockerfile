FROM python:3.12-slim
WORKDIR /app
COPY simulator.py .
# Stdlib only — no pip install needed.
ENV BACKEND_URL=http://backend:8080
# Run as a non-root user — the simulator only makes outbound calls, so it never needs root.
RUN useradd --uid 10001 --create-home simulator
USER simulator
CMD ["python", "-u", "simulator.py"]
