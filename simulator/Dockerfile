FROM python:3.12-slim
WORKDIR /app
COPY simulator.py .
# Stdlib only — no pip install needed.
ENV BACKEND_URL=http://backend:8080
CMD ["python", "-u", "simulator.py"]
