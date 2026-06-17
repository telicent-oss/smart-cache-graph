from kafka import KafkaProducer

producer = KafkaProducer(bootstrap_servers='localhost:9092')

headers = [
    ("Distribution-Id", b"test4"),
    ("Security-Label", b"(classification=O&(permitted_organisations=GBR.ALL)&(permitted_nationalities=GBR))"),
    ("Content-Type", b"application/n-quads"),
]

messages = [
    "<https://example.org/ns#test001> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ies.data.gov.uk/ontology/ies4#WorkOfDocumentation> .",
    "<https://example.org/ns#test002> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ies.data.gov.uk/ontology/ies4#WorkOfDocumentation> .",
    "<https://example.org/ns#test003> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ies.data.gov.uk/ontology/ies4#WorkOfDocumentation> .",
]

topic = "RDF"

for msg in messages:
    producer.send(topic, value=msg.encode('utf-8'), headers=headers)

producer.flush()
print("Done")