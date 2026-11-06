"""Package the already-built JAR, existing truststore, and EB runtime configuration."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

backend = Path(__file__).resolve().parents[1]
jar = backend / 'target/portfolio-0.0.1-SNAPSHOT.jar'
truststore = backend / 'certs/aiven-truststore.jks'
with ZipFile(jar) as artifact:
    assert not any('application-env.properties' in name for name in artifact.namelist()), 'Local credentials in JAR'
    assert 'Main-Class: org.springframework.boot.loader.launch.JarLauncher' in artifact.read('META-INF/MANIFEST.MF').decode()
with ZipFile(backend / 'target/elastic-beanstalk.zip', 'w', ZIP_DEFLATED) as bundle:
    bundle.write(jar, 'app.jar')
    bundle.write(backend / 'Procfile', 'Procfile')
    bundle.write(truststore, 'certs/aiven-truststore.jks')
    for directory in ('.ebextensions', '.platform'):
        for path in sorted((backend / directory).rglob('*')):
            if path.is_file():
                bundle.write(path, path.relative_to(backend))
print('Created backend/target/elastic-beanstalk.zip')
